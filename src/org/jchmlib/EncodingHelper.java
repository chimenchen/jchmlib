package org.jchmlib;

@SuppressWarnings({"unused", "WeakerAccess"})
class EncodingTable {

    final String charset;
    final String country;
    final int lcid;
    final int codepage;
    final int icharset;
    final String encoding;

    EncodingTable(String charset, String country,
            int lcid, int codepage,
            int icharset, String encoding) {
        this.charset = charset;
        this.country = country;
        this.lcid = lcid;
        this.codepage = codepage;
        this.icharset = icharset;
        this.encoding = encoding;
    }
}

class EncodingHelper {

    private static final EncodingTable[] encodingTable = new EncodingTable[]{
            new EncodingTable("Afrikaans", "", 0x0436, 1252, 0, "CP1252"),
            new EncodingTable("Albanian", "", 0x041C, 1250, 238, "CP1250"),
            new EncodingTable("Arabic", "Algeria", 0x1401, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Bahrain", 0x3C01, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Egypt", 0x0C01, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Iraq", 0x0801, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Jordan", 0x2C01, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Kuwait", 0x3401, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Lebanon", 0x3001, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Libya", 0x1001, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Morocco", 0x1801, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Oman", 0x2001, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Qatar", 0x4001, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Saudi Arabia", 0x0401, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Syria", 0x2801, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "Tunisia", 0x1C01, 1256, 0, "CP1256"),
            new EncodingTable("Arabic", "United Arab Emirates", 0x3801, 1256, 178,
                    "CP1256"),
            new EncodingTable("Arabic", "Yemen", 0x2401, 1256, 0, "CP1256"),
            new EncodingTable("Armenian", "", 0x042B, 0, 0, "Latin1"),
            new EncodingTable("Azeri", "Cyrillic", 0x082C, 1251, 0, "CP1251"),
            new EncodingTable("Azeri", "Latin", 0x042C, 1254, 162, "CP1254"),
            new EncodingTable("Basque", "", 0x042D, 1252, 0, "CP1252"),
            new EncodingTable("Belarusian", "", 0x0423, 1251, 0, "CP1251"),
            new EncodingTable("Bulgarian", "", 0x0402, 1251, 0, "CP1251"),
            new EncodingTable("Catalan", "", 0x0403, 1252, 0, "CP1252"),
            new EncodingTable("Chinese", "China", 0x0804, 936, 134, "GBK"),
            new EncodingTable("Chinese", "Hong Kong SAR", 0x0C04, 950, 136, "Big5"),
            new EncodingTable("Chinese", "Macau SAR", 0x1404, 950, 136, "Big5"),
            new EncodingTable("Chinese", "Singapore", 0x1004, 936, 134, "GB2313"),
            new EncodingTable("Chinese", "Taiwan", 0x0404, 950, 136, "Big5"), // traditional
            new EncodingTable("Croatian", "", 0x041A, 1250, 238, "CP1250"),
            new EncodingTable("Czech", "", 0x0405, 1250, 238, "CP1250"),
            new EncodingTable("Danish", "", 0x0406, 1252, 0, "CP1252"),
            new EncodingTable("Dutch", "Belgium", 0x0813, 1252, 0, "CP1252"),
            new EncodingTable("Dutch", "The Netherlands", 0x0413, 1252, 0, "CP1252"),
            new EncodingTable("English", "Australia", 0x0C09, 1252, 0, "CP1252"),
            new EncodingTable("English", "Belize", 0x2809, 1252, 0, "CP1252"),
            new EncodingTable("English", "Canada", 0x1009, 1252, 0, "CP1252"),
            new EncodingTable("English", "Caribbean", 0x2409, 1252, 0, "CP1252"),
            new EncodingTable("English", "Ireland", 0x1809, 1252, 0, "CP1252"),
            new EncodingTable("English", "Jamaica", 0x2009, 1252, 0, "CP1252"),
            new EncodingTable("English", "New Zealand", 0x1409, 1252, 0, "CP1252"),
            new EncodingTable("English", "Phillippines", 0x3409, 1252, 0, "CP1252"),
            new EncodingTable("English", "South Africa", 0x1C09, 1252, 0, "CP1252"),
            new EncodingTable("English", "Trinidad", 0x2C09, 1252, 0, "CP1252"),
            new EncodingTable("English", "United Kingdom", 0x0809, 1252, 0, "CP1252"),
            new EncodingTable("English", "United States", 0x0409, 1252, 0, "CP1252"),
            new EncodingTable("Estonian", "", 0x0425, 1257, 186, "CP1257"),
            new EncodingTable("FYRO Macedonian", "", 0x042F, 1251, 0, "CP1251"),
            new EncodingTable("Faroese", "", 0x0438, 1252, 0, "CP1252"),
            new EncodingTable("Farsi", "", 0x0429, 1256, 178, "CP1256"),
            new EncodingTable("Finnish", "", 0x040B, 1252, 0, "CP1252"),
            new EncodingTable("French", "Belgium", 0x080C, 1252, 0, "CP1252"),
            new EncodingTable("French", "Canada", 0x0C0C, 1252, 0, "CP1252"),
            new EncodingTable("French", "France", 0x040C, 1252, 0, "CP1252"),
            new EncodingTable("French", "Luxembourg", 0x140C, 1252, 0, "CP1252"),
            new EncodingTable("French", "Switzerland", 0x100C, 1252, 0, "CP1252"),
            new EncodingTable("German", "Austria", 0x0C07, 1252, 0, "CP1252"),
            new EncodingTable("German", "Germany", 0x0407, 1252, 0, "CP1252"),
            new EncodingTable("German", "Liechtenstein", 0x1407, 1252, 0, "CP1252"),
            new EncodingTable("German", "Luxembourg", 0x1007, 1252, 0, "CP1252"),
            new EncodingTable("German", "Switzerland", 0x0807, 1252, 0, "CP1252"),
            new EncodingTable("Greek", "", 0x0408, 1253, 161, "CP1253"),
            new EncodingTable("Hebrew", "", 0x040D, 1255, 177, "CP1255"),
            new EncodingTable("Hindi", "", 0x0439, 0, 0, "Latin1"),
            new EncodingTable("Hungarian", "", 0x040E, 1250, 238, "CP1250"),
            new EncodingTable("Icelandic", "", 0x040F, 1252, 0, "CP1252"),
            new EncodingTable("Indonesian", "", 0x0421, 1252, 0, "CP1252"),
            new EncodingTable("Italian", "Italy", 0x0410, 1252, 0, "CP1252"),
            new EncodingTable("Italian", "Switzerland", 0x0810, 1252, 0, "CP1252"),
            new EncodingTable("Japanese", "", 0x0411, 932, 128, "Shift-JIS"),
            new EncodingTable("Korean", "", 0x0412, 949, 129, "eucKR"),
            new EncodingTable("Latvian", "", 0x0426, 1257, 186, "CP1257"),
            new EncodingTable("Lithuanian", "", 0x0427, 1257, 186, "CP1257"),
            new EncodingTable("Malay", "Brunei", 0x083E, 1252, 0, "CP1252"),
            new EncodingTable("Malay", "Malaysia", 0x043E, 1252, 0, "CP1252"),
            new EncodingTable("Maltese", "", 0x043A, 0, 0, "Latin1"),
            new EncodingTable("Marathi", "", 0x044E, 0, 0, "Latin1"),
            new EncodingTable("Norwegian", "Bokmal", 0x0414, 1252, 0, "CP1252"),
            new EncodingTable("Norwegian", "Nynorsk", 0x0814, 1252, 0, "CP1252"),
            new EncodingTable("Polish", "", 0x0415, 1250, 238, "CP1250"),
            new EncodingTable("Portuguese", "Brazil", 0x0416, 1252, 0, "CP1252"),
            new EncodingTable("Portuguese", "Portugal", 0x0816, 1252, 0, "CP1252"),
            new EncodingTable("Romanian", "Romania", 0x0418, 1250, 238, "CP1250"),
            new EncodingTable("Russian", "", 0x0419, 1251, 204, "CP1251"),
            new EncodingTable("Sanskrit", "", 0x044F, 0, 0, "Latin1"),
            new EncodingTable("Serbian", "Cyrillic", 0x0C1A, 1251, 0, "CP1251"),
            new EncodingTable("Serbian", "Latin", 0x081A, 1250, 238, "CP1250"),
            new EncodingTable("Setsuana", "", 0x0432, 1252, 0, "CP1252"),
            new EncodingTable("Slovak", "", 0x041B, 1250, 238, "CP1250"),
            new EncodingTable("Slovenian", "", 0x0424, 1250, 238, "CP1250"),
            new EncodingTable("Spanish", "Argentina", 0x2C0A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Bolivia", 0x400A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Chile", 0x340A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Colombia", 0x240A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Costa Rica", 0x140A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Dominican Republic", 0x1C0A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Ecuador", 0x300A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "El Salvador", 0x440A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Guatemala", 0x100A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Honduras", 0x480A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Mexico", 0x080A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Nicaragua", 0x4C0A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Panama", 0x180A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Paraguay", 0x3C0A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Peru", 0x280A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Puerto Rico", 0x500A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Spain", 0x0C0A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Uruguay", 0x380A, 1252, 0, "CP1252"),
            new EncodingTable("Spanish", "Venezuela", 0x200A, 1252, 0, "CP1252"),
            new EncodingTable("Swahili", "", 0x0441, 1252, 0, "CP1252"),
            new EncodingTable("Swedish", "Finland", 0x081D, 1252, 0, "CP1252"),
            new EncodingTable("Swedish", "Sweden", 0x041D, 1252, 0, "CP1252"),
            new EncodingTable("Tamil", "", 0x0449, 0, 0, "TSCII"),
            new EncodingTable("Tatar", "", 0x0444, 1251, 204, "CP1251"),
            new EncodingTable("Thai", "", 0x041E, 874, 222, "TIS-620"),
            new EncodingTable("Turkish", "", 0x041F, 1254, 162, "CP1254"),
            new EncodingTable("Ukrainian", "", 0x0422, 1251, 0, "CP1251"),
            new EncodingTable("Urdu", "", 0x0420, 1256, 178, "CP1256"),
            new EncodingTable("Uzbek", "Cyrillic", 0x0843, 1251, 0, "CP1251"),
            new EncodingTable("Uzbek", "Latin", 0x0443, 1254, 162, "CP1254"),
            new EncodingTable("Vietnamese", "", 0x042A, 1258, 163, "CP1258"),
            new EncodingTable("Xhosa", "", 0x0434, 1252, 0, "CP1252"),
            new EncodingTable("Zulu", "", 0x0435, 1252, 0, "CP1252")
    };

    public static String findEncoding(int lcid) {
        for (EncodingTable anEncodingTable : encodingTable) {
            if (anEncodingTable.lcid == lcid) {
                return anEncodingTable.encoding;
            }
        }
        return "UTF-8";
    }

}

