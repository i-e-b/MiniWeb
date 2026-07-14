using System.Collections;
using System.Diagnostics.CodeAnalysis;
using System.IO;
using System.Text;

namespace MiniWeb.Core;

/// <summary>
/// A generic templating tool that uses <see cref="HNode"/> to parse a set of HTML/XML documents
/// plus parameter information, and create a single document in response.
/// </summary>
public class TemplateEngine
{
    private readonly IAssetLoader?      _assets;
    private readonly IPermissionsCheck? _permissions;
    private readonly INetworkRequest?   _network;

    /// <summary>
    /// Create a template engine with the given asset loader
    /// </summary>
    /// <param name="assets">Asset loader, used to load sub-views</param>
    /// <param name="permissions">[Optional] permission manager for hiding content. All gated content is hidden if not provided</param>
    /// <param name="network">[Optional] used to load sub-views by URL. Url sub-views not supported if not provided </param>
    public TemplateEngine(IAssetLoader? assets, IPermissionsCheck? permissions, INetworkRequest? network)
    {
        _assets = assets;
        _permissions = permissions;
        _network = network;
    }

    /// <summary>
    /// Try to render a template
    /// </summary>
    /// <param name="path">Path to resource that contains the template body as a UTF8 stream</param>
    /// <param name="model">Key/Value set of parameters that can be used in the template</param>
    public TemplateResponse Render(string path, object? model)
    {
        return Render(_assets?.LoadAsset(path), model);
    }

    /// <summary>
    /// Try to render a template
    /// </summary>
    /// <param name="utf8Bytes">UTF8 bytes of the template</param>
    /// <param name="model">Key/Value set of parameters that can be used in the template</param>
    public TemplateResponse Render(byte[] utf8Bytes, object? model)
    {
        var stream = new MemoryStream(utf8Bytes);
        stream.Seek(0, SeekOrigin.Begin);
        return Render(stream, model);
    }

    /// <summary>
    /// Try to render a template
    /// </summary>
    /// <param name="template">The template body as a UTF8 stream</param>
    /// <param name="model">Key/Value set of parameters that can be used in the template</param>
    public TemplateResponse Render(Stream? template, object? model)
    {
        var result = new TemplateResponse
        {
            Model = model,
            Success = false,
            Result = null
        };

        if (template is null) return result;

        var reader = new StreamReader(template, new UTF8Encoding());
        var test   = new StringBuilder();

        while (true)
        {
            var line = reader.ReadLine();
            if (line is null) break;

            test.AppendLine(InlineReplace(result, line));
        }

        var node = HNode.Parse(test.ToString());

        var pageOut = new DocumentBuilder(result);
        RecurseTemplate(node, pageOut, null, model);

        result.Success = true;
        result.Result = pageOut.GetResult();
        return result;
    }

    /// <summary>
    /// Recurse through the HNode tree, rendering output and interpreting directives
    /// </summary>
    private void RecurseTemplate(HNode node, DocumentBuilder page, object? item, object? model){
        // This method is the largest in the project.
        // Template directives are never self-closing (i.e. <tag/>).
        // Known directives:
        //
        //   <_for {path}>{content}</_for>         -- repeat contents for each non-null item in model
        //   <_else>{content}</_else>              -- show if previous _for did not display
        //   <_>{path}</_>                         -- insert value from model
        //   <_needs {perms...}>{content}</_needs> -- show content only if user has at least one of the permissions
        //   <_view {params...}></_view>           -- inject a sub-view into the page
        //
        // Paths
        //
        //   a           -->  model.a         (or model.get("a") )
        //   a.b.c       -->  model.a.b.c
        //   #           -->  item
        //   #.x.y       -->  item.x.y

        // Content or recursion?
        if (node is { IsUnderscored: false, Children.Count: < 1 }) {
            // not a container. Slap in contents
            page.AddRange(node.Src, node.SrcStart, node.SrcEnd + 1);
        } else {
            if (node.IsUnderscored){
                // Special things
                var tagParams = new List<(string,string)>();
                var tag = DecomposeTag(page, StrRange(node.Src, node.SrcStart, node.ContStart), tagParams);
                switch (tag){
                    case "_": // plain data lookup
                    {
                        var path = node.InnerText();
                        if (path == "#") page.Add(FindField(item, ""));
                        else if (path.StartsWith("#.")) page.Add(FindField(item, path.Substring(2)));
                        else page.Add(FindField(model, path));
                        break;
                    }
                    case "_for":
                    {
                        page.LastBlockWasHidden = true;
                        if (tagParams.Count < 1) {
                            page.Warning("invalid <_for> tag");
                            break;
                        }

                        var path = tagParams.FirstOrDefault().Item1;
                        object? items;
                        if (path.StartsWith("#.")) items = FindField(item, path.Substring(2));
                        else items = FindField(model, path);

                        if (items is string str)
                        {
                            // 'for' items is a string. Show if not empty
                            if (!string.IsNullOrWhiteSpace(str))
                            {
                                page.LastBlockWasHidden = false;
                                foreach (var child in node.Children) RecurseTemplate(child, page, str, model);
                            }
                        }
                        else if (items is IEnumerable listItems)
                        {
                            // 'for' item is a list. Repeat contents
                            foreach (var subItem in listItems)
                            {
                                page.LastBlockWasHidden = false;
                                foreach (var child in node.Children) RecurseTemplate(child, page, subItem, model);
                            }
                        }
                        else if (items is bool b)
                        {
                            // 'for' items is bool. Show if true
                            if (b)
                            {
                                page.LastBlockWasHidden = false;
                                foreach (var child in node.Children) RecurseTemplate(child, page, true, model);
                            }
                        }
                        else if (items != null)
                        {
                            // something else. Show if not null{
                            page.LastBlockWasHidden = false;
                            foreach (var child in node.Children) RecurseTemplate(child, page, items, model);
                        }

                        break;
                    }
                    case "_else":
                    {
                        if (page.LastBlockWasHidden) {
                            foreach (var child in node.Children) RecurseTemplate(child, page, item, model);
                        }
                        break;
                    }
                    case "_needs":
                    {
                        if (_permissions is null)
                        {
                            page.LastBlockWasHidden = true;
                            page.Warning("Permission tag seen, but no permission source");
                        }
                        else
                        {
                            var required = tagParams.Select(v => v.Item1).ToArray();
                            if (required.Length > 0 && _permissions.GrantPermission(required))
                            {
                                page.LastBlockWasHidden = false;
                                foreach (var child in node.Children) RecurseTemplate(child, page, item, model);
                            }
                            else
                            {
                                page.LastBlockWasHidden = true;
                            }
                        }

                        break;
                    }
                    case "_view":
                    {
                        InjectViewBlock(tagParams, model, item, page);
                        break;
                    }
                    default: {
                        page.Warning("Unknown tag: '" + tag + "'");
                        break;
                    }
                }
            } else {// Normal HTML
                // Opening tag. False for comments, scripts, etc
                if (node.SrcStart < node.ContStart) page.AddRange(node.Src, node.SrcStart, node.ContStart);
                // Recurse each child
                foreach (var child in node.Children) RecurseTemplate(child, page, item, model);
                // Closing tag. False for comments, scripts, etc
                if (node.ContEnd < node.SrcEnd) page.AddRange(node.Src, node.ContEnd + 1, node.SrcEnd + 1);
            }
        }
    }

    /// <summary>
    /// Handle sub-view blocks by calling back out through the template system and injecting results into string builder.
    /// Returns number of extra lines consumed from the input template
    /// </summary>
    /// <param name="attributes">Attributes on the sub-view tag</param>
    /// <param name="model">model values for the view</param>
    /// <param name="cursorItem"><c>item</c> for view iteration</param>
    /// <param name="page">output target</param>
    private void InjectViewBlock(List<(string, string)> attributes, object? model, object? cursorItem, DocumentBuilder page)
    {
        var url       = attributes.Where(v => v.Item1 == "url" && !string.IsNullOrWhiteSpace(v.Item2)).Select(v=>v.Item2).FirstOrDefault();
        var path      = attributes.Where(v => v.Item1 == "path" && !string.IsNullOrWhiteSpace(v.Item2)).Select(v=>v.Item2).FirstOrDefault();
        var modelPath = attributes.Where(v => v.Item1 == "model" && !string.IsNullOrWhiteSpace(v.Item2)).Select(v=>v.Item2).FirstOrDefault();

        if (!string.IsNullOrWhiteSpace(url)) { // should do a GET to this URL
            if (_network is null)
            {
                page.Warning("View from URL not supported: no network proxy");
                return;
            }

            try {
                var requestModel = GetViewModelObjectByPath(model, cursorItem, modelPath);
                var response  = _network.GetResponse(url, requestModel);
                page.Add(response);
                return;
            } catch (Exception ex) {
                page.Warning("URL view block failed:" + ex);
                return;
            }
        }

        // exit early if there is no path
        if (string.IsNullOrWhiteSpace(path)) {
            page.Warning("Invalid view block: no url or path. Check page mark-up.");
            return;
        }

        // Try to find a model item. We will assume page's model if no prefix is given.
        // If we can't find anything, we will pass in `null`
        var viewModel = GetViewModelObjectByPath(model, cursorItem, modelPath);

        // Try to load and render the view into the output
        try
        {
            var result = Render(_assets?.LoadAsset(path), viewModel);
            if (result.Success)
            {
                page.Add(result.Result);
                foreach (var warning in result.Warnings)
                {
                    page.Warning($"Subview warning. Path={path}; Message='{warning}';");
                }
            }
            else
            {
                page.Warning($"Subview rendering failed. Path={path};");
            }

        } catch (Exception ex) {
            page.Warning("Failed to load view block:" + ex);
        }
    }

    /// <summary>
    /// try to find an object given a `model.path.to.thing` or `item.path.to.thing`
    /// </summary>
    private static object? GetViewModelObjectByPath(object? model, object? cursorItem, string? modelPath)
    {
        if (string.IsNullOrWhiteSpace(modelPath)) return model;

        if (modelPath.StartsWith("model"))
        {
            // looks like a page model reference
            if (modelPath == "model")
            {
                // view should get the whole model
                return model;
            }

            // need to find by path
            return FindField(model, StripFirstDotItem(modelPath));
        }

        if (modelPath.StartsWith("item"))
        {
            // looks like a 'for' block item reference
            if (modelPath == "item")
            {
                // view should get the whole iterated item
                return cursorItem;
            }

            // need to find by path
            return FindField(cursorItem, StripFirstDotItem(modelPath));
        }

        // assume page model reference
        return FindField(model, modelPath);
    }

    /// <summary>
    /// Remove start of string up to and including the first '.'
    /// </summary>
    private static string StripFirstDotItem(string path) {
        var index = path.IndexOf('.');
        if (index < 0) return "";
        return index + 1 >= path.Length ? "" : path[(index + 1)..];
    }

    /// <summary>
    /// take "&lt;_>", "&lt;_for thing>" etc., and split into parts. Array is always at least 1 element, never null
    /// </summary>
    private static string DecomposeTag(DocumentBuilder page, string tag, List<(string, string)> attributes) {
        var i   = 1;
        var end = tag.Length;
        page.ResetTag();

        // get tag
        for (; i < end; i++){
            var c = tag[i];
            if (c == ' ') break;
            if (c == '>') break;
            page.TagAdd(c);
        }
        var tagOut = page.TagStr();

        // get params
        var inQuote  = false;
        var hasValue = false;
        for (; i < end; i++)
        {
            var c = tag[i];
            if (c == '"') inQuote = !inQuote;
            else if (!inQuote && c == '=') hasValue = true;
            else if (!inQuote && c == ' ')
            {
                page.TagAddToMap(hasValue, attributes);
                hasValue = false;
                page.ResetTag();
            }
            else
            {
                if (c == '>') break;

                if (hasValue) page.ValueAdd(c);
                else page.KeyAdd(c);
            }
        }

        page.TagAddToMap(hasValue, attributes);
        return tagOut;
    }

    /// <summary>
    /// Replace <c>_$ var_name $_</c> templates
    /// </summary>
    private static string InlineReplace(TemplateResponse context, string line)
    {
        // Hopefully the most common: do nothing.
        if (!line.Contains("_$") || !line.Contains("$_")) return line;

        // scan through the line, replacing as we go
        var sb   = new StringBuilder();
        var left = 0;
        var end  = line.Length - 1;
        while (left < end)
        {
            var next = line.IndexOf("_$", left, StringComparison.Ordinal);
            if (next < 0) break;

            var term = line.IndexOf("$_", left, StringComparison.Ordinal);
            if (term < next)
            {
                sb.Append(StrRange(line, left, term + 2));
                left = term + 2;
                continue;
            }

            var key = StrRange(line, next + 2, term).Trim();
            sb.Append(StrRange(line, left, next));

            var obj = FindField(context.Model, key);
            if (obj != null)
            {
                sb.Append(ConvertToString(obj));
            }
            else
            {
                context.Warnings.Add("Not found: _$" + key + "$_");
            }

            left = term + 2;
        }

        sb.Append(line.Substring(left));
        return sb.ToString();
    }

    /// <summary>
    /// Injection point for custom string conversion
    /// </summary>
    private static string ConvertToString(object? obj)
    {
        if (obj is null) return "";
        if (obj is DateTime dt) return dt.ToString("yyyy-MM-dd HH:mm:ss");
        return obj.ToString() ?? "";
    }

    /// <summary>
    /// Java compatibility helper
    /// </summary>
    private static string StrRange(string src, int startIdx, int endIdx)
    {
        return src.Substring(startIdx, endIdx - startIdx);
    }

    /// <summary>
    /// Recurse through the template model looking for a value.
    /// This will search through object properties, dictionary entries, and IEnumerable lists.
    /// Search is case sensitive.
    /// </summary>
    private static object? FindField(object? model, string name)
    {
        if (model is null) return null;

        if (name.Length < 1)
        {
            // special case: <_></_> : output the model itself as a string
            return model.ToString() ?? "";
        }

        if (!name.Contains('.') && !name.Contains('$')) // simple case
        {
            return TryGetField(model, name);
        }

        // We have a dotted path. Split the path up, and step through each element.
        var src      = model;
        var pathBits = name.Split('.', '$');

        // walk down the chain
        foreach (var bit in pathBits)
        {
            if (src == null) return null;

            src = TryGetField(src, bit);
        }

        return src;
    }

    private static object? TryGetField(object? model, string name)
    {
        if (model is null) return null;
        if (model is IDictionary map)
        {
            try
            {
                return map[name];
            }
            catch (Exception)
            {
                return null;
            }
        }

        if (int.TryParse(name, out var idx))
        {
            if (model is IEnumerable list)
            {
                return GetIndex(list, idx);
            }

            return null;
        }

        var prop = model.GetType().GetProperty(name);
        if (prop is not null)
        {
            try
            {
                return prop.GetValue(model);
            }
            catch
            {
                return null;
            }
        }

        var field = model.GetType().GetField(name);
        if (field is not null)
        {
            try
            {
                return field.GetValue(model);
            }
            catch
            {
                return null;
            }
        }

        return null;
    }

    private static object? GetIndex(IEnumerable list, int idx)
    {
        if (idx < 0) return null;

        if (list is IList indexable)
        {
            if (idx >= indexable.Count) return null;
            return indexable[idx];
        }

        var iter = list.GetEnumerator();
        try
        {
            for (var i = 0; i < idx; i++)
            {
                if (!iter.MoveNext()) return null;
            }

            return iter.Current;
        }
        finally
        {
            if (iter is IDisposable disposable) disposable.Dispose();
        }
    }

    /// <summary>
    /// Context for building a template result
    /// </summary>
    private class DocumentBuilder {
        private readonly StringBuilder    _output;
        private readonly StringBuilder    _tag;
        private readonly StringBuilder    _key;
        private readonly StringBuilder    _value;
        private readonly TemplateResponse _template;

        /// <summary>
        /// For 'else' paths
        /// </summary>
        public bool LastBlockWasHidden;

        /// <summary>
        /// Start a new document
        /// </summary>
        public DocumentBuilder(TemplateResponse template)
        {
            LastBlockWasHidden = false;
            _template = template;
            _output = new StringBuilder();
            _tag = new StringBuilder();
            _key = new StringBuilder();
            _value = new StringBuilder();
        }

        /// <summary>
        /// Append to output
        /// </summary>
        public void Add(object? t)
        {
            if (t is null) return;
            _output.Append(ConvertToString(t));
        }

        /// <summary>
        /// Add a substring to output
        /// </summary>
        public void AddRange(string t, int left, int right)
        {
            _output.Append(t, left, right - left);
        }

        /// <summary>
        /// Add a warning message to the template result
        /// </summary>
        public void Warning(string msg)
        {
            _template.Warnings.Add("Error: " + msg);
        }

        /// <summary>
        /// Get the constructed document
        /// </summary>
        public string GetResult()
        {
            return _output.ToString();
        }

        public void ResetTag() {
            _tag.Clear();
            _key.Clear();
            _value.Clear();
        }

        public void TagAdd(char c) {_tag.Append(c);}
        public string TagStr() {return _tag.ToString();}

        public void TagAddToMap(bool hasValue, List<(string, string)> attributes) {
            if (_key.Length > 0) {
                if (hasValue) attributes.Add((_key.ToString(), _value.ToString()));
                else attributes.Add((_key.ToString(), _key.ToString()));
            }
        }
        public void ValueAdd(char c) {_value.Append(c);}
        public void KeyAdd(char c) {_key.Append(c);}
    }
}

/// <summary>
/// The path to a document template, and the model data to go in it, and some details for the hot-reload system.
/// This is all we need to render a final page.
/// </summary>
[SuppressMessage("ReSharper", "UnusedAutoPropertyAccessor.Global")]
[SuppressMessage("ReSharper", "PropertyCanBeMadeInitOnly.Global")]
public class TemplateResponse
{
    /// <summary>
    /// Provider for script and navigation interactions
    /// </summary>
    public IPageInteractions? InteractionProvider { get; set; }

    /// <summary>
    /// Parameter model used to expand the template
    /// </summary>
    public object? Model { get; set; }

    /// <summary>
    /// If not null, the web view will be redirected to this url
    /// </summary>
    public string? RedirectUrl { get; set; }

    /// <summary>
    /// True if the template could be rendered (even if there are warnings)
    /// </summary>
    public bool Success { get; set; }

    /// <summary>
    /// Resulting document as a UTF8 stream. Will be <c>null</c> on failure.
    /// </summary>
    public string? Result { get; set; }

    /// <summary>
    /// List of non critical warnings during template rendering
    /// </summary>
    public List<string> Warnings { get; } = [];

    /// <summary>
    /// Path to template file
    /// </summary>
    public string? TemplatePath { get; set; }

    /// <summary>
    /// If <c>true</c>, the web view should clear 'back' history
    /// </summary>
    public bool ShouldClearHistory { get; set; }
}

/// <summary>
/// Controller response trying to trigger a file download
/// </summary>
public class FileDownloadResponse : TemplateResponse
{
    /// <summary>
    /// Raw data for file
    /// </summary>
    public byte[] FileData { get; init; } = [];

    /// <summary>
    /// Mime type of the file
    /// </summary>
    public string MimeType { get; init; } = "application/octet-stream";

    /// <summary>
    /// Suggested file name
    /// </summary>
    public string FileName { get; init; } = "download.dat";
}