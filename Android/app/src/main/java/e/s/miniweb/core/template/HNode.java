package e.s.miniweb.core.template;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Very simple and liberal HTML parser for templating
 * The tree we output is just a set of ranges over the original string.
 * */
public class HNode {
    /** The entire document */
    public static final int TYPE_ROOT = 0;
    /** Text or raw data. Doesn't look like mark-up */
    public static final int TYPE_TEXT = 1;
    /** Normal element. Looks like &lt;elem attr>...&lt;/elem> */
    public static final int TYPE_NODE = 2;
    /** Self-closed element. Looks like &lt;elem attr/> */
    public static final int TYPE_ELEM = 3;
    /** Self-enclosed directive. Looks like &lt;!directive...>*/
    public static final int TYPE_DIRK = 4;
    /** "Comment" or script. Looks like &lt;!-- ... ---> or &lt;script>...&lt;/script>*/
    public static final int TYPE_SKIT = 5;

    /** Child nodes. Can be empty, never null */
    public final List<HNode> children = new ArrayList<>();

    /** parent node. Can be null */
    public final HNode parent;

    /** Reference to original input string */
    public final String Src;

    /** true if the element tag name starts with '_' */
    public boolean isUnderscored;

    /** Parser type of this node */
    public int type;

    /** start of outer HTML (total span of this node, including children) */
    public int srcStart;
    /** end of outer HTML (total span of this node, including children) */
    public int srcEnd;

    /** start of inner HTML (internal span of this node, including children) */
    public int contStart;
    /** end of inner HTML (internal span of this node, including children) */
    public int contEnd;

    /** Parse a HTML fragment to a node tree */
    public static HNode parse(String src) {
        HNode base = new HNode(null, src);

        parseRecursive(base, src, 0);
        base.contStart = base.srcStart = 0;
        base.contEnd = base.srcEnd = src.length() - 1;
        base.type = TYPE_ROOT;

        return base;
    }

    /** Get the text contained by this element, or empty string */
    public String innerText() {
        try {
            StringBuilder sb = new StringBuilder();
            for (HNode node: this.children) innerTextRecursive(node, sb);
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString(){
        try {
            String typeStr = "?";
            switch (type){
                case TYPE_NODE:typeStr="node";break;
                case TYPE_ELEM:typeStr="elem";break;
                case TYPE_SKIT:typeStr="raw";break;
                case TYPE_DIRK:typeStr="directive";break;
                case TYPE_ROOT:typeStr="root";break;
                case TYPE_TEXT:typeStr="text";break;
            }
            if (children.isEmpty()) {
                int end = contEnd + 1;
                if (end > Src.length()) end--;
                if (srcStart == contStart) return typeStr+", text: " + Src.substring(contStart, end);
                return typeStr+", "+Src.substring(srcStart, contStart)+" => '"+Src.substring(contStart, end)+"'"; // just the opening tag
            } else {
                return typeStr+", "+Src.substring(srcStart, contStart)+"; contains " + children.size();
            }
        } catch (Exception ex){
            return "fail: "+srcStart+".."+srcEnd;
        }
    }

    //region internals


    private static void innerTextRecursive(HNode node, StringBuilder out) {
        if (node.children.size() < 1) {
            // not a container. Slap in contents
            out.append(node.Src, node.srcStart, node.srcEnd + 1);
            return;
        }

        // Opening tag
        if (node.srcStart < node.contStart) { // false for comments, scripts, etc
            out.append(node.Src, node.srcStart, node.contStart);
        }

        // Recurse each child
        for (HNode child : node.children) innerTextRecursive(child, out);

        // Closing tag
        if (node.contEnd < node.srcEnd) { // false for comments, scripts, etc
            out.append(node.Src, node.contEnd + 1, node.srcEnd + 1);
        }
    }

    /** String.indexOf() value for not found */
    private static final int NOT_FOUND = -1;


    protected HNode(HNode parent, String src){
        this.parent=parent;
        this.isUnderscored = false;
        Src = src;
    }
    protected HNode(HNode parent, int srcStart, int contStart, int contEnd, int srcEnd, int type){
        this.isUnderscored = false;
        this.parent = parent;Src = parent.Src;this.type = type;
        this.srcStart = srcStart;this.contStart = contStart;this.contEnd = contEnd;this.srcEnd = srcEnd;
    }

    /** recurse, return the offset we ended */
    @SuppressWarnings("UnnecessaryContinue")
    private static int parseRecursive(HNode target, String src, int offset){
        int lastIndex = src.length()-1;
        int left = offset;
        target.contStart = offset;
        while (left <= lastIndex){
            int leftAngle = src.indexOf('<', left);
            int more = lastIndex - leftAngle;

            if (leftAngle == NOT_FOUND){
                // no more markup in the source.
                // if left..end is not empty, add a text node and return
                leftAngle = lastIndex;
                if (leftAngle > left){
                    HNode.textChild(target, left, lastIndex);
                }
                return leftAngle; // return because EOF
            } else if (leftAngle >= lastIndex) {
                // sanity check: got a '<' as the last character
                HNode.textChild(target, left, lastIndex);
                return lastIndex; // return because it's broken.
            } else {
                // we have the start of an element <...>
                int rightAngle = src.indexOf('>', left);

                // sanity checks
                if (rightAngle == NOT_FOUND || more < 1 || rightAngle == left + 1) { // `<`...EOF (x2)   or  `<>`
                    // invalid markup? Consider the rest as text and bail out
                    HNode.textChild(target, left, lastIndex);
                    return lastIndex + 1; // return because it's broken.
                }
                if (src.charAt(leftAngle+1) == '/') { //   </...
                    // If there is any content up to this point, add it as a 'text' child
                    if (leftAngle > left){ //???
                        HNode.textChild(target, left, leftAngle - 1);
                    }

                    // end of our own tag
                    target.srcEnd = rightAngle;
                    target.contEnd = leftAngle - 1;
                    if (target.contEnd == 0) target.contEnd = -1;
                    // TODO: unwind until we get to a matching tag, to handle bad markup.
                    return rightAngle + 1; // return because it's the end of this tag.
                } else if (src.charAt(leftAngle+1) == '!') { // <!...
                    if (more > 2 && src.charAt(leftAngle+2) == '-' && src.charAt(leftAngle+3) == '-') {
                        // `<!-- ... -->`
                        left = processOtherBlock(target, left, "-->") + 1;
                    } else { // `<!...>`
                        HNode.directiveChild(target, left, rightAngle);
                        left = rightAngle+1;
                    }
                    continue;
                } else if (src.charAt(leftAngle+1)=='?'){ // <? ... ?>
                    // this is an XML directive.
                    HNode.directiveChild(target, left, rightAngle);
                    left = rightAngle+1;
                    continue;
                } else { // a child tag
                    // check for 'empty' element
                    if (src.charAt(rightAngle - 1) == '/') { // goes like <elem ... />
                        HNode.elemChild(target, left, rightAngle);
                        left = rightAngle + 1;
                        continue;
                    } else if (isScript(target, leftAngle)){
                        left = processOtherBlock(target, left, "</script>") + 1;
                        continue;
                    } else {
                        // This looks like a real node. Recurse

                        // If there is any content up to this point, add it as a 'text' child
                        if (leftAngle > left){
                            HNode.textChild(target, left, leftAngle - 1);
                        }

                        // Start the child node and recurse it
                        HNode node = new HNode(target, target.Src);
                        node.type = TYPE_NODE;
                        node.isUnderscored = src.charAt(leftAngle + 1) == '_';
                        node.srcStart = leftAngle;
                        left = parseRecursive(node, src, rightAngle + 1);
                        if (node.contEnd < 1 || node.srcEnd < 1){
                            Log.w("html", "fail");
                        }
                        target.children.add(node);
                    }
                }
            }
        }
        return left;
    }

    private static boolean isScript(HNode target, int offset) {
        final String script = "<script";

        if (offset+7 > target.Src.length()) return false;
        String tag = target.Src.substring(offset, offset+7);

        return tag.equals(script);
    }

    private static int processOtherBlock(HNode target, int left, String terminator) {
        int right = target.Src.indexOf(terminator, left);
        if (right == NOT_FOUND) right = target.Src.length();
        else right += terminator.length();
        target.children.add(new HNode(target, left, right,right,right, TYPE_SKIT));
        return right;
    }

    private static void elemChild(HNode target, int left, int right) {
        target.children.add(new HNode(target, left, right+1, right+1, right, TYPE_ELEM));
    }

    private static void directiveChild(HNode target, int left, int right) {
        target.children.add(new HNode(target, left, right, right, right, TYPE_DIRK));
    }

    private static void textChild(HNode target, int left, int right) {
        target.children.add(new HNode(target, left, left, right, right, TYPE_TEXT));
    }


    //endregion
}
