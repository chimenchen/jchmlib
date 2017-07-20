package org.jchmlib.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A decompressor for LZX format
 */
public class LZXInflator {

    /* some constants defined by the LZX specification */
    static final int LZX_MIN_MATCH = 2;
    static final int LZX_MAX_MATCH = 257;
    static final int LZX_NUM_CHARS = 256;
    static final int LZX_BLOCKTYPE_INVALID = 0;   /* also blocktypes 4-7 invalid */
    static final int LZX_BLOCKTYPE_VERBATIM = 1;
    static final int LZX_BLOCKTYPE_ALIGNED = 2;
    static final int LZX_BLOCKTYPE_UNCOMPRESSED = 3;
    static final int LZX_PRETREE_NUM_ELEMENTS = 20;
    static final int LZX_ALIGNED_NUM_ELEMENTS = 8;   /* aligned offset tree #elements */
    static final int LZX_NUM_PRIMARY_LENGTHS = 7;   /* this one missing from spec! */
    static final int LZX_NUM_SECONDARY_LENGTHS = 249; /* length tree #elements */
    /* LZX huffman defines: tweak tablebits as desired */
    static final int LZX_PRETREE_MAXSYMBOLS = LZX_PRETREE_NUM_ELEMENTS;
    static final int LZX_PRETREE_TABLEBITS = 6;
    static final int LZX_MAINTREE_MAXSYMBOLS = LZX_NUM_CHARS + 50 * 8;
    static final int LZX_MAINTREE_TABLEBITS = 12;
    static final int LZX_LENGTH_MAXSYMBOLS = LZX_NUM_SECONDARY_LENGTHS + 1;
    static final int LZX_LENGTH_TABLEBITS = 12;
    static final int LZX_ALIGNED_MAXSYMBOLS = LZX_ALIGNED_NUM_ELEMENTS;
    static final int LZX_ALIGNED_TABLEBITS = 7;
    static final int LZX_LENTABLE_SAFETY = 64; /* we allow length table decoding overruns */
    /* LZX uses what it calls 'position slots' to represent match offsets.
     * What this means is that a small 'position slot' number and a small
     * offset from that slot are encoded instead of one large offset for
     * every match.
     * - position_base is an index to the position slot bases
     * - extra_bits states how many bits of offset-from-base data is needed.
     */
    static final int extra_bits[] = {
            0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
            7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14,
            15, 15, 16, 16, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17,
            17, 17, 17
    };
    static final long position_base[] = {
            0, 1, 2, 3, 4, 6, 8, 12,
            16, 24, 32, 48, 64, 96, 128, 192,
            256, 384, 512, 768, 1024, 1536, 2048, 3072,
            4096, 6144, 8192, 12288, 16384, 24576, 32768, 49152,
            65536, 98304, 131072, 196608, 262144, 393216, 524288, 655360,
            786432, 917504, 1048576, 1179648, 1310720, 1441792, 1572864, 1703936,
            1835008, 1966080, 2097152
    };
    byte[] window;        /* the actual decoding window              */
    long window_size;     /* window size (32Kb through 2Mb)          */
    long actual_size;     /* window size when it was first allocated */
    long window_posn;     /* current offset within the window        */
    long R0, R1, R2;      /* for the LRU offset system               */
    int main_elements;   /* number of main tree elements            */
    int header_read;     /* have we started decoding at all yet?    */
    int block_type;      /* type of this block                      */
    long block_length;    /* uncompressed length of this block       */
    long block_remaining; /* uncompressed bytes still left to decode */
    long frames_read;     /* the number of CFDATA blocks processed   */
    int intel_filesize;  /* magic header value used for transform   */
    int intel_curpos;    /* current offset in transform space       */
    int intel_started;   /* have we seen any translatable data yet? */
    int[] pretree_table = new int[(1 << LZX_PRETREE_TABLEBITS) + (LZX_PRETREE_MAXSYMBOLS << 1)];
    byte[] pretree_len = new byte[LZX_PRETREE_MAXSYMBOLS + LZX_LENTABLE_SAFETY];
    int[] maintree_table = new int[(1 << LZX_MAINTREE_TABLEBITS) + (LZX_MAINTREE_MAXSYMBOLS << 1)];
    byte[] maintree_len = new byte[LZX_MAINTREE_MAXSYMBOLS + LZX_LENTABLE_SAFETY];
    int[] length_table = new int[(1 << LZX_LENGTH_TABLEBITS) + (LZX_LENGTH_MAXSYMBOLS << 1)];
    byte[] length_len = new byte[LZX_LENGTH_MAXSYMBOLS + LZX_LENTABLE_SAFETY];
    int[] aligned_table = new int[(1 << LZX_ALIGNED_TABLEBITS) + (LZX_ALIGNED_MAXSYMBOLS << 1)];
    byte[] aligned_len = new byte[LZX_ALIGNED_MAXSYMBOLS + LZX_LENTABLE_SAFETY];

    public LZXInflator(int iwindow) {
        int wndsize = 1 << iwindow;
        int i, posn_slots;

        // LZX supports window sizes of 2^15 (32Kb) through 2^21 (2Mb)
        // if a previously allocated window is big enough, keep it
        if (iwindow < 15 || iwindow > 21) {
            return;
        }

        // allocate associated window
        window = new byte[wndsize];

        actual_size = wndsize;
        window_size = wndsize;

        // calculate required position slots
        if (iwindow == 20) {
            posn_slots = 42;
        } else if (iwindow == 21) {
            posn_slots = 50;
        } else {
            posn_slots = iwindow << 1;
        }

        /* alternatively **/
        /* posn_slots=i=0; while (i < wndsize) i += 1 << extra_bits[posn_slots++]; */

        // initialize other state
        R0 = 1;
        R1 = 1;
        R2 = 1;
        main_elements = LZX_NUM_CHARS + (posn_slots << 3);
        header_read = 0;
        frames_read = 0;
        block_remaining = 0;
        block_type = LZX_BLOCKTYPE_INVALID;
        intel_curpos = 0;
        intel_started = 0;
        window_posn = 0;

        // initialise tables to 0 (because deltas will be applied to them)
        for (i = 0; i < LZX_MAINTREE_MAXSYMBOLS; i++) {
            maintree_len[i] = 0;
        }
        for (i = 0; i < LZX_LENGTH_MAXSYMBOLS; i++) {
            length_len[i] = 0;
        }
    }

    public void reset() {
        int i;

        R0 = 1;
        R1 = 1;
        R2 = 1;
        header_read = 0;
        frames_read = 0;
        block_remaining = 0;
        block_type = LZX_BLOCKTYPE_INVALID;
        intel_curpos = 0;
        intel_started = 0;
        window_posn = 0;

        /* initialise tables to 0 (because deltas will be applied to them) */
        for (i = 0; i < LZX_MAINTREE_MAXSYMBOLS + LZX_LENTABLE_SAFETY; i++) {
            maintree_len[i] = 0;
        }
        for (i = 0; i < LZX_LENGTH_MAXSYMBOLS + LZX_LENTABLE_SAFETY; i++) {
            length_len[i] = 0;
        }
    }

    int make_decode_table(int[] table, byte[] length, long nbits, long nsyms) {
        int sym;
        long leaf;
        int bit_num = 1;
        long fill;
        long pos = 0; // the current position in the decode table
        long table_mask = 1 << nbits;
        long bit_mask = table_mask >>> 1; // don't do 0 length codes
        long next_symbol = bit_mask; // base of allocation for long codes

        // fill entries for codes short enough for a direct mapping
        while (bit_num <= nbits) {
            for (sym = 0; sym < nsyms; sym++) {
                if (length[sym] == bit_num) {
                    leaf = pos;

                    if ((pos += bit_mask) > table_mask) {
                        return 1; // table overrun
                    }

                    // fill all possible lookups of this symbol with the symbol itself
                    fill = bit_mask;
                    while (fill-- > 0) {
                        table[(int) (leaf++)] = sym;
                    }
                }
            }
            bit_mask >>>= 1;
            bit_num++;
        }

        // if there are any codes longer than nbits
        if (pos != table_mask) {
            // clear the remainder of the table
            for (sym = (int) pos; sym < table_mask; sym++) {
                table[sym] = 0;
            }

            // give ourselves room for codes to grow by up to 16 more bits
            pos <<= 16;
            table_mask <<= 16;
            bit_mask = 1 << 15;

            while (bit_num <= 16) {
                for (sym = 0; sym < nsyms; sym++) {
                    if (length[sym] == bit_num) {
                        leaf = pos >>> 16;
                        for (fill = 0; fill < bit_num - nbits; fill++) {
                            // if this path hasn't been taken yet, 'allocate' two entries
                            if (table[(int) leaf] == 0) {
                                table[(int) (next_symbol << 1)] = 0;
                                table[(int) (next_symbol << 1) + 1] = 0;
                                table[(int) leaf] = (int) (next_symbol++);
                            }
                            // follow the path and select either left or right for next bit
                            leaf = table[(int) leaf] << 1;
                            if (((pos >>> (15 - fill)) & 1) != 0) {
                                leaf++;
                            }
                        }
                        table[(int) leaf] = sym;

                        if ((pos += bit_mask) > table_mask) {
                            return 1; // table overflow
                        }
                    }
                }
                bit_mask >>>= 1;
                bit_num++;
            }
        }

        // full table?
        if (pos == table_mask) {
            return 0;
        }

        // either erroneous table, or all elements are 0 - let's find out.
        for (sym = 0; sym < nsyms; sym++) {
            if (length[sym] != 0) {
                return 1;
            }
        }
        return 0;
    }

    int read_lens(BitReader bitReader, byte[] lens, long first, long last) {
        long x, y;
        int z;

        for (x = 0; x < 20; x++) {
            y = bitReader.readBits(4);
            pretree_len[(int) x] = (byte) y;
        }

        int ret = make_decode_table(pretree_table, pretree_len, LZX_PRETREE_TABLEBITS,
                LZX_PRETREE_MAXSYMBOLS);
        if (ret != 0) {
            return 1;
        }

        for (x = first; x < last; ) {
            z = readHuffSym(bitReader, pretree_table, pretree_len, LZX_PRETREE_TABLEBITS,
                    LZX_PRETREE_MAXSYMBOLS);
            if (z == 17) {
                y = bitReader.readBits(4);
                y += 4;
                while (y-- != 0) {
                    lens[(int) (x++)] = 0;
                }
            } else if (z == 18) {
                y = bitReader.readBits(5);
                y += 20;
                while (y-- != 0) {
                    lens[(int) (x++)] = 0;
                }
            } else if (z == 19) {
                y = bitReader.readBits(1);
                y += 4;
                z = readHuffSym(bitReader, pretree_table, pretree_len, LZX_PRETREE_TABLEBITS,
                        LZX_PRETREE_MAXSYMBOLS);
                z = lens[(int) x] - z;
                if (z < 0) {
                    z += 17;
                }
                while (y-- != 0) {
                    lens[(int) (x++)] = (byte) z;
                }
            } else {
                z = lens[(int) x] - z;
                if (z < 0) {
                    z += 17;
                }
                lens[(int) (x++)] = (byte) z;
            }
        }

        return 0;
    }

    // FIXME: review usages of readHuffSym, handle error conditions
    private int readHuffSym(BitReader bitReader, int[] tbl, byte[] lentbl,
            int tbl_bits, int max_symbols) {
        bitReader.ensureBits(16);
        long i, j;
        int var;
        int temp = bitReader.peekBits(tbl_bits);

        i = tbl[temp];
        if (i >= max_symbols) {
            j = 1L << (64 - tbl_bits);

            do {
                j >>>= 1;
                i <<= 1;
                i |= (((bitReader.bitBuffer & j) != 0) ? 1 : 0);
                if (j == 0) {
                    return -1;
                }
            } while ((i = tbl[(int) i]) >= max_symbols);
        }

        var = (int) i;
        j = lentbl[(int) i];
        bitReader.removeBits((int) j);

        return var;
    }

    /**
     * Decompress a block of bytes.
     *
     * @param inBuf buffer holding the compressed data.
     * @param inLen length (in bytes) of the compressed data.
     * @param outLen length (in bytes) of the decompressed data.
     * @return byte buffer decompressed
     */
    public ByteBuffer decompress(ByteBuffer inBuf, int inLen, int outLen) {
        ByteBuffer outBuf = ByteBuffer.allocate(outLen);

        long i, j, k, match_offset;
        int this_run, aligned_bits;
        int match_length, length_footer, extra, verbatim_bits;
        int rundest_offset, runsrc_offset;
        int togo = outLen, main_element;
        int num; // just for loop

        BitReader bitReader = new BitReader(inBuf, true);

        // read header if necessary
        if (header_read != 1) {
            i = 0;
            j = 0;
            k = bitReader.readBits(1);
            if (k == 1) {
                i = bitReader.readBits(16);
                j = bitReader.readBits(16);
            }
            intel_filesize = (int) ((i << 16) | j); // or 0 if not encoded
            header_read = 1;
        }

        // main decoding loop
        while (togo > 0) {
            if (block_remaining == 0) {
                if (block_type == LZX_BLOCKTYPE_UNCOMPRESSED) {
                    if (((int) block_length & 1) != 0) {
                        inBuf.get(); // realign bitstream to word
                    }
                    bitReader.init(inBuf, true);
                }

                block_type = bitReader.readBits(3);
                i = bitReader.readBits(16);
                j = bitReader.readBits(8);
                block_remaining = block_length = (i << 8) | j;

                switch (block_type) {
                    case LZX_BLOCKTYPE_ALIGNED:
                        for (i = 0; i < 8; i++) {
                            j = bitReader.readBits(3);
                            aligned_len[(int) i] = (byte) j;
                        }
                        make_decode_table(aligned_table, aligned_len,
                                LZX_ALIGNED_TABLEBITS,
                                LZX_ALIGNED_MAXSYMBOLS);
                        // rest of aligned header is same as verbatim

                        // no break

                    case LZX_BLOCKTYPE_VERBATIM:
                        read_lens(bitReader, maintree_len, 0, 256);
                        read_lens(bitReader, maintree_len, 256, main_elements);
                        make_decode_table(maintree_table, maintree_len,
                                LZX_MAINTREE_TABLEBITS,
                                LZX_MAINTREE_MAXSYMBOLS);

                        if (maintree_len[0xE8] != 0) {
                            intel_started = 1;
                        }

                        read_lens(bitReader, length_len, 0, LZX_NUM_SECONDARY_LENGTHS);
                        make_decode_table(length_table, length_len,
                                LZX_LENGTH_TABLEBITS,
                                LZX_LENGTH_MAXSYMBOLS);
                        break;

                    case LZX_BLOCKTYPE_UNCOMPRESSED:
                        intel_started = 1; // because we can't assume otherwise
                        // get up to 16 pad bits into the buffer
                        bitReader.ensureBits(16);
                        if (bitReader.bitsBuffered > 16) {
                            // and align the bitstream
                            inBuf.position(inBuf.position() - 2);
                        }
                        inBuf.order(ByteOrder.LITTLE_ENDIAN);
                        R0 = inBuf.getInt();
                        R1 = inBuf.getInt();
                        R2 = inBuf.getInt();
                        break;
                    default:
                        // System.out.println("block type " + block_type);
                        return null;
                }
            }

            // TODO: buffer exhausting check

            while ((this_run = (int) block_remaining) > 0 && togo > 0) {
                if (this_run > togo) {
                    this_run = togo;
                }
                togo -= this_run;
                block_remaining -= this_run;

                // apply 2^x-1 mask
                window_posn &= window_size - 1;
                // runs can't straddle the window wraparound
                if ((window_posn + this_run) > window_size) {
                    // System.out.println("(window_posn + this_run) > window_size");
                    return null;
                }

                // TODO:
                switch (block_type) {
                    case LZX_BLOCKTYPE_VERBATIM:
                        while (this_run > 0) {
                            main_element = readHuffSym(
                                    bitReader,
                                    maintree_table,
                                    maintree_len,
                                    LZX_MAINTREE_TABLEBITS,
                                    LZX_MAINTREE_MAXSYMBOLS);
                            if (main_element < LZX_NUM_CHARS) {
                                // literal: 0 to LZX_NUM_CHARS -1
                                // TODO:
                                window[(int) (window_posn++)] = (byte) main_element;
                                this_run--;
                            } else {
                                // match: LZX_NUM_CHARS + ((slot<<3) | length_header ( 3bits))
                                main_element -= LZX_NUM_CHARS;

                                match_length = main_element & LZX_NUM_PRIMARY_LENGTHS;

                                if (match_length == LZX_NUM_PRIMARY_LENGTHS) {

                                    length_footer = readHuffSym(bitReader, length_table,
                                            length_len,
                                            LZX_LENGTH_TABLEBITS,
                                            LZX_LENGTH_MAXSYMBOLS);

                                    match_length += length_footer;
                                }
                                match_length += LZX_MIN_MATCH;

                                match_offset = main_element >> 3;

                                if (match_offset > 2) {
                                    // not repeated offset
                                    if (match_offset != 3) {
                                        extra = extra_bits[(int) match_offset];
                                        verbatim_bits = bitReader.readBits(extra);
                                        match_offset = position_base[(int) match_offset] - 2
                                                + verbatim_bits;
                                    } else {
                                        match_offset = 1;
                                    }

                                    // update repeated offset LRU queue
                                    R2 = R1;
                                    R1 = R0;
                                    R0 = match_offset;
                                } else if (match_offset == 0) {
                                    match_offset = R0;
                                } else if (match_offset == 1) {
                                    match_offset = R1;
                                    R1 = R0;
                                    R0 = match_offset;
                                } else { // match_offset == 2
                                    match_offset = R2;
                                    R2 = R0;
                                    R0 = match_offset;
                                }

                                rundest_offset = (int) window_posn;
                                runsrc_offset = (int) (rundest_offset - match_offset);
                                window_posn += match_length;
                                if (window_posn > window_size) {
                                    // System.out.println("window_posn > window_size");
                                    return null;
                                }
                                this_run -= match_length;

                                // copy any wrapped around source data
                                while ((runsrc_offset < 0) && (match_length-- > 0)) {
                                    window[rundest_offset++] = window[runsrc_offset
                                            + (int) window_size];
                                    runsrc_offset++;
                                }
                                // copy match data - no worries about destination wraps
                                while (match_length-- > 0) {
                                    window[rundest_offset++] = window[runsrc_offset++];
                                }
                            }
                        }
                        break;

                    case LZX_BLOCKTYPE_ALIGNED:
                        // System.out.println("Aligned:");
                        while (this_run > 0) {
                            main_element = readHuffSym(bitReader, maintree_table,
                                    maintree_len,
                                    LZX_MAINTREE_TABLEBITS,
                                    LZX_MAINTREE_MAXSYMBOLS);

                            if (main_element < LZX_NUM_CHARS) {
                                // literal: 0 to LZX_NUM_CHARS-1
                                window[(int) (window_posn++)] = (byte) main_element;
                                this_run--;
                            } else {
                                // match: LZX_NUM_CHARS + ((slot<<3) | length_header (3 bits))
                                main_element -= LZX_NUM_CHARS;

                                match_length = main_element & LZX_NUM_PRIMARY_LENGTHS;
                                if (match_length == LZX_NUM_PRIMARY_LENGTHS) {
                                    length_footer = readHuffSym(bitReader, length_table,
                                            length_len,
                                            LZX_LENGTH_TABLEBITS,
                                            LZX_LENGTH_MAXSYMBOLS);

                                    match_length += length_footer;
                                }
                                match_length += LZX_MIN_MATCH;

                                match_offset = main_element >> 3;

                                if (match_offset > 2) {
                                    // not repeated offset
                                    extra = extra_bits[(int) match_offset];
                                    match_offset = position_base[(int) match_offset] - 2;
                                    if (extra > 3) {
                                        // verbatim and aligned bits
                                        extra -= 3;
                                        verbatim_bits = bitReader.readBits(extra);
                                        match_offset += (verbatim_bits << 3);
                                        aligned_bits = readHuffSym(
                                                bitReader,
                                                aligned_table,
                                                aligned_len,
                                                LZX_ALIGNED_TABLEBITS,
                                                LZX_ALIGNED_MAXSYMBOLS);

                                        match_offset += aligned_bits;
                                    } else if (extra == 3) {
                                        // aligned bits only
                                        aligned_bits = readHuffSym(
                                                bitReader,
                                                aligned_table,
                                                aligned_len,
                                                LZX_ALIGNED_TABLEBITS,
                                                LZX_ALIGNED_MAXSYMBOLS);

                                        match_offset += aligned_bits;
                                    } else if (extra > 0) { // extra==1, extra==2
                                        // verbatim bits only
                                        verbatim_bits = bitReader.readBits(extra);
                                        match_offset += verbatim_bits;
                                    } else { // extra == 0
                                        // ???
                                        match_offset = 1;
                                    }

                                    // update repeated offset LRU queue
                                    R2 = R1;
                                    R1 = R0;
                                    R0 = match_offset;
                                } else if (match_offset == 0) {
                                    match_offset = R0;
                                } else if (match_offset == 1) {
                                    match_offset = R1;
                                    R1 = R0;
                                    R0 = match_offset;
                                } else { // match_offset == 2
                                    match_offset = R2;
                                    R2 = R0;
                                    R0 = match_offset;
                                }

                                rundest_offset = (int) window_posn;
                                runsrc_offset = rundest_offset - (int) match_offset;
                                window_posn += match_length;
                                if (window_posn > window_size) {
                                    // System.out.println("window_posn " + window_posn +
                                    //         " > window_size 2 " + window_size);
                                    return null;
                                }
                                this_run -= match_length;

                                // copy any wrapped around source data
                                while ((runsrc_offset < 0) && (match_length-- > 0)) {
                                    window[rundest_offset++] = window[runsrc_offset
                                            + (int) window_size];
                                    runsrc_offset++;
                                }
                                // copy match data - no worries about destination wraps
                                while (match_length-- > 0) {
                                    window[rundest_offset++] = window[runsrc_offset++];
                                }

                            }
                        }
                        break;

                    case LZX_BLOCKTYPE_UNCOMPRESSED:
                        for (num = 0; num < this_run && inBuf.hasRemaining(); num++) {
                            window[(int) (window_posn++)] = inBuf.get();
                        }
                        break;

                    default:
                        // System.out.println("default::::null" + block_type);
                        return null; // might as well

                }

            }
        }

        if (togo != 0) {
            // System.out.println("togo != 0");
            return null;
        }

        int start = (int) ((window_posn == 0) ? window_size : window_posn) - outLen;

        // System.out.println("LZXInflator.decompress\t " + start + " + " + outLen);
        outBuf.mark();
        for (num = 0; num < outLen; num++) {
            outBuf.put(window[start + num]);
        }
        outBuf.reset();

        // TODO: intel E8 decoding
        // if ((frames_read++ < 32768) && intel_filesize != 0) {
        // System.out.println("Intel E8 decoding not done. Broken");
        // }

        return outBuf;
    }  // decompress() ends here.

}
