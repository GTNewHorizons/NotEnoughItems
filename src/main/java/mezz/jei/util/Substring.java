package mezz.jei.util;

public class Substring {

    private final String string;
    private final int offset;
    private final int length;

    public Substring(String string) {
        this(string, 0, string.length());
    }

    public Substring(Substring subString) {
        this(subString.string, subString.offset, subString.length);
    }

    public Substring(String string, int offset) {
        this(string, offset, string.length() - offset);
    }

    public Substring(String string, int offset, int length) {
        assert length >= 0;
        assert offset >= 0;
        assert offset + length <= string.length();
        this.string = string;
        this.offset = offset;
        this.length = length;
    }

    public Substring substring(int offset) {
        return new Substring(string, this.offset + offset, this.length - offset);
    }

    public Substring shorten(int amount) {
        return new Substring(string, this.offset, this.length - amount);
    }

    public Substring append(char newChar) {
        assert this.offset + this.length < this.string.length();
        assert charAt(this.length) == newChar;
        return new Substring(string, this.offset, this.length + 1);
    }

    public boolean isEmpty() {
        return this.length == 0;
    }

    public char charAt(int index) {
        return this.string.charAt(this.offset + index);
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        // noinspection StringEquality
        if (this.string == other) {
            if (this.length >= len && (this.offset + toffset == ooffset)) {
                return true;
            }
        }
        return this.string.regionMatches(this.offset + toffset, other, ooffset, len);
    }

    public boolean regionMatches(Substring word, int lenToMatch) {
        if (lenToMatch > this.length) {
            return false;
        }
        return word.regionMatches(0, this.string, this.offset, lenToMatch);
    }

    public boolean isPrefix(Substring other) {
        return other.startsWith(this);
    }

    public boolean startsWith(Substring other) {
        return regionMatches(other, other.length());
    }

    public int length() {
        return length;
    }

    public String commit() {
        return string.substring(offset, offset + length);
    }

}
