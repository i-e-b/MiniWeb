using System.Text;

// ReSharper disable UseObjectOrCollectionInitializer

namespace MiniWeb.Core;

/**
 * Very simple and liberal HTML parser for templating
 * The tree we output is just a set of ranges over the original string.
 * */
public class HNode
{
    /// <summary> Type of the node </summary>
    public enum HNodeType
    {
        /** The entire document */
        Root = 0,

        /** Text or raw data. Doesn't look like mark-up.
         * This might be whitespace, check 'IsWhitespace()' */
        Text = 1,

        /** Normal element. Looks like &lt;elem attr>...&lt;/elem> */
        Node = 2,

        /** Self-closed element. Looks like &lt;elem attr/> */
        Element = 3,

        /** Self-enclosed directive. Looks like &lt;!directive...>*/
        Directive = 4,

        /** "Comment" or script. Looks like &lt;!-- ... --> or &lt;script>...&lt;/script>*/
        CommentOrScript = 5
    }

    /** Child nodes. Can be empty, never null */
    public readonly List<HNode> Children = new();

    /** parent node. Can be null */
    public readonly HNode? Parent;

    /** Reference to original input string */
    private readonly string _src;

    /** true if the element tag name starts with '_'. This is intended for use with templating. */
    public bool IsUnderscored;

    /** true if any problems were found in this node */
    public bool Errors;

    /** Parser type of this node */
    public HNodeType Type;

    /** start of outer HTML (total span of this node, including children) */
    public int SrcStart;

    /** end of outer HTML (total span of this node, including children) */
    public int SrcEnd;

    /** start of inner HTML (internal span of this node, including children) */
    public int ContStart;

    /** end of inner HTML (internal span of this node, including children) */
    public int ContEnd;

    /** Parse a HTML fragment to a node tree */
    public static HNode Parse(string src)
    {
        var basis = new HNode(null, src);

        ParseRecursive(basis, src, 0);
        basis.ContStart = basis.SrcStart = 0;
        basis.ContEnd = basis.SrcEnd = src.Length - 1;
        basis.Type = HNodeType.Root;

        return basis;
    }

    /** Get the text contained by this element and its children, excluding mark-up.
     * Returns empty string if not content is present
     */
    public string InnerText()
    {
        var sb = new StringBuilder();
        InnerTextRecursive(this, sb);
        return sb.ToString();
    }

    /// <summary>
    /// Render this node and its children as HTML/XML.
    /// The text is recovered from the original input string.
    /// </summary>
    public string SourceString()
    {
        return _src.Substring(SrcStart, 1 + SrcEnd - SrcStart);
    }

    /// <summary>
    /// Generate a diagnostic description of the HNode tree
    /// </summary>
    public override string ToString()
    {
        var typeStr = Type switch
        {
            HNodeType.Node => "node",
            HNodeType.Element => "elem",
            HNodeType.CommentOrScript => "raw",
            HNodeType.Directive => "directive",
            HNodeType.Root => "root",
            HNodeType.Text => "text",
            _ => "?"
        };
        if (Children.Count < 1)
        {
            var end = ContEnd + 1;
            if (end > _src.Length) end--;
            if (SrcStart == ContStart) return typeStr + ", text: " + _src.Substring(ContStart, end - ContStart);
            return typeStr + ", " + _src.Substring(SrcStart, ContStart - SrcStart) + " => '" + _src.Substring(ContStart, end - ContStart) + "'"; // just the opening tag
        }

        return typeStr + ", " + _src.Substring(SrcStart, ContStart - SrcStart) + "; contains " + Children.Count;
    }

    /// <summary>
    /// Returns true if this node is a text node that contains only
    /// whitespace characters. False for any other nodes, or if
    /// non-whitespace in the content.
    /// </summary>
    public bool IsWhitespace()
    {
        if (Type != HNodeType.Text) return false;
        if (ContStart == ContEnd) return true; // is empty
        return string.IsNullOrWhiteSpace(_src.Substring(ContStart, (ContEnd - ContStart) + 1));
    }

    /// <summary>
    /// Text of the opening side of the tag.
    /// Null if no tag for this node.
    /// </summary>
    public string? TagStart()
    {
        if (Type == HNodeType.Root || Type == HNodeType.Text) return null;
        return _src.Substring(SrcStart, ContStart - SrcStart);
    }
    
    /// <summary>
    /// Text of the closing side of the tag, if any
    /// Null if no tag for this node.
    /// </summary>
    public string? TagEnd()
    {
        if (Type != HNodeType.Node) return null;
        if (SrcEnd == ContEnd) return null;
        return _src.Substring(ContEnd+1, SrcEnd - ContEnd);
    }

    /// <summary>
    /// Return the original source string
    /// </summary>
    public string Src => _src;

    #region internals

    private static void InnerTextRecursive(HNode node, StringBuilder outp)
    {
        if (node.Children.Count < 1)
        {
            // Not a container. Add our own contents, if any
            if (node.ContStart <= node.ContEnd) outp.Append(node._src, node.ContStart, (node.ContEnd - node.ContStart) + 1);
            return;
        }

        // Recurse each child
        foreach (var child in node.Children) InnerTextRecursive(child, outp);
    }
    
    /** String.indexOf() value for not found */
    private const int NotFound = -1;


    private HNode(HNode? parent, string src)
    {
        Parent = parent;
        IsUnderscored = false;
        Errors = false;
        _src = src;
    }

    private HNode(HNode parent, int srcStart, int contStart, int contEnd, int srcEnd, HNodeType type)
    {
        IsUnderscored = false;
        Parent = parent;
        _src = parent._src;
        Type = type;
        SrcStart = srcStart;
        ContStart = contStart;
        ContEnd = contEnd;
        SrcEnd = srcEnd;
    }

    /** recurse, return the offset we ended */
    private static int ParseRecursive(HNode target, string src, int offset)
    {
        var lastIndex = src.Length - 1;
        var left = offset;
        target.ContStart = offset;
        while (left <= lastIndex)
        {
            var leftAngle = src.IndexOf('<', left);
            var more = lastIndex - leftAngle;

            if (leftAngle == NotFound)
            {
                // no more markup in the source.
                // if left..end is not empty, add a text node and return
                leftAngle = lastIndex;
                if (leftAngle > left)
                {
                    TextChild(target, left, lastIndex);
                }

                return leftAngle; // return because EOF
            }

            if (leftAngle >= lastIndex)
            {
                // sanity check: got a '<' as the last character
                TextChild(target, left, lastIndex);
                return lastIndex; // return because it's broken.
            }

            // we have the start of an element <...>
            var rightAngle = src.IndexOf('>', left);

            // sanity checks
            if (rightAngle == NotFound || more < 1 || rightAngle == left + 1)
            {
                // `<`...EOF (x2)   or  `<>`
                // invalid markup? Consider the rest as text and bail out
                TextChild(target, left, lastIndex);
                return lastIndex + 1; // return because it's broken.
            }

            switch (src[leftAngle + 1]) // what comes after the '<'
            {
                case '/':
                {
                    //   </...
                    // If there is any content up to this point, add it as a 'text' child
                    if (leftAngle > left) { TextChild(target, left, leftAngle - 1); }

                    // end of our own tag
                    target.SrcEnd = rightAngle;
                    target.ContEnd = leftAngle - 1;
                    if (target.ContEnd == 0) target.ContEnd = target.SrcEnd;

                    //Console.WriteLine($"TAG END? '{target.TagEnd()}'");

                    return rightAngle + 1; // return because it's the end of this tag.
                }
                case '!':
                {
                    // <!...
                    if (more > 2 && src[leftAngle + 2] == '-' && src[leftAngle + 3] == '-')
                    {
                        // `<!-- ... -->`
                        left = ProcessOtherBlock(target, left, "-->") + 1;
                    }
                    else
                    {
                        // `<!...>`
                        DirectiveChild(target, left, rightAngle);
                        left = rightAngle + 1;
                    }

                    continue;
                }
                case '?': // <? ... ?>
                    // this is an XML directive.
                    DirectiveChild(target, left, rightAngle);
                    left = rightAngle + 1;
                    continue;
            }

            // a child tag
            // check for 'empty' element
            if (src[rightAngle - 1] == '/')
            {
                // goes like <elem ... />
                ElemChild(target, left, rightAngle);
                left = rightAngle + 1;
                continue;
            }

            if (IsScript(target, leftAngle))
            {
                left = ProcessOtherBlock(target, left, "</script>") + 1;
                continue;
            }
            // This looks like a real node. Recurse

            // If there is any content up to this point, add it as a 'text' child
            if (leftAngle > left)
            {
                TextChild(target, left, leftAngle - 1); // this includes whitespace
            }

            // Start the child node and recurse it
            var node = new HNode(target, target._src);
            node.Type = HNodeType.Node;
            node.IsUnderscored = src[leftAngle + 1] == '_';
            node.SrcStart = leftAngle;

            left = ParseRecursive(node, src, rightAngle + 1);
            if (node.ContEnd < 1 || node.SrcEnd < 1)
            {
                // couldn't find a valid end-of-node
                // Add logging or reporting here
                /*Console.Write($"BAD NODE: '{node.TagStart()}' [");
                foreach (var c in node.Children)
                {
                    Console.Write(c.TagStart());
                }
                Console.Write("] ");
                Console.WriteLine(";");*/

                node.Errors = true;
            }

            target.Children.Add(node);
        }

        return left;
    }

    private static bool IsScript(HNode target, int offset)
    {
        const string script = "<script";

        if (offset + 7 > target._src.Length) return false;
        var tag = target._src.Substring(offset, 7);

        return tag.Equals(script);
    }

    private static int ProcessOtherBlock(HNode target, int left, string terminator)
    {
        var right = target._src.IndexOf(terminator, left, StringComparison.Ordinal);
        if (right == NotFound) right = target._src.Length;
        else right += terminator.Length;
        target.Children.Add(new HNode(target, left, right, right, right, HNodeType.CommentOrScript));
        return right;
    }

    private static void ElemChild(HNode target, int left, int right)
    {
        target.Children.Add(new HNode(target, left, right + 1, right + 1, right, HNodeType.Element));
    }

    private static void DirectiveChild(HNode target, int left, int right)
    {
        target.Children.Add(new HNode(target, left, right, right, right, HNodeType.Directive));
    }

    private static void TextChild(HNode target, int left, int right)
    {
        target.Children.Add(new HNode(target, left, left, right, right, HNodeType.Text));
    }

    #endregion
}