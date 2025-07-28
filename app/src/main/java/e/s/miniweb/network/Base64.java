package e.s.miniweb.network;

/** Helpers for Base64 conversion */
public class Base64 {
    /** Most commonly used Base64 type. Also known as RFC 1421, RFC 3501, RFC 4648  */
    public final String Base64Common = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    /** Common URL/URI safe variant. Described in RFC 4648 as 'base64url URL- and filename-safe standard' */
    public final String Base64Url = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+_=";

    /**
     * Encode a byte array into a Base64 string using the custom key
     */
    public static String toB64(int[] input, String base64type) {
        var output = new StringBuilder();
        int chr1, chr2, chr3, enc1, enc2, enc3, enc4;
        var i = 0;

        while (i < input.length) {
            chr1 = input[i++];
            if (chr1 < 0) break;

            chr2 = (i < input.length) ? input[i++] : 0;
            chr3 = (i < input.length) ? input[i++] : 0;

            enc1 = chr1 >> 2;
            enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
            enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
            enc4 = chr3 & 63;

            if (chr2 <= 0) enc3 = enc4 = 64;
            else if (chr3 <= 0) enc4 = 64;

            output.append(base64type.charAt(enc1));
            output.append(base64type.charAt(enc2));
            output.append(base64type.charAt(enc3));
            output.append(base64type.charAt(enc4));

        }

        return output.toString();
    }

    /**
     * Decode a custom keyed Base64 string into a byte array
     */
    public static int[] fromB64(String input, String base64type) {
        var output = new int[input.length() * 3];
        int chr1, chr2, chr3;
        int enc1, enc2, enc3, enc4;
        var i = 0;
        var j = 0;

        while (i < input.length()) {

            enc1 = base64type.indexOf(input.charAt(i++));
            enc2 = base64type.indexOf(input.charAt(i++));
            enc3 = base64type.indexOf(input.charAt(i++));
            enc4 = base64type.indexOf(input.charAt(i++));

            chr1 = (enc1 << 2) | (enc2 >> 4);
            chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
            chr3 = ((enc3 & 3) << 6) | enc4;

            output[j++] = chr1;

            if (enc3 != 64) {
                output[j++] = chr2;
            }
            if (enc4 != 64) {
                output[j++] = chr3;
            }

        }

        while (j < output.length) {
            output[j++] = -1;
        }

        return output;
    }
}
