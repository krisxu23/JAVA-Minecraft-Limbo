/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.nanit.limbo.connection.pipeline;

import io.netty.util.ByteProcessor;

public class VarIntByteDecoder implements ByteProcessor {

    /** Maximum number of bytes a Minecraft VarInt can occupy (5 * 7 = 35 bits, enough for 32-bit int). */
    private static final int MAX_VARINT_BYTES = 5;

    private int readVarInt;
    private int bytesRead;
    private DecodeResult result = DecodeResult.TOO_SHORT;

    @Override
    public boolean process(byte k) {
        readVarInt |= (k & 0x7F) << bytesRead++ * 7;
        if (bytesRead > MAX_VARINT_BYTES) {
            result = DecodeResult.TOO_BIG;
            return false;
        }
        // Continuation bit: 0 = last byte, 1 = more bytes follow
        if ((k & 0x80) == 0) {
            result = DecodeResult.SUCCESS;
            return false;
        }
        return true;
    }

    public int getReadVarInt() {
        return readVarInt;
    }

    public int getBytesRead() {
        return bytesRead;
    }

    public DecodeResult getResult() {
        return result;
    }

    public enum DecodeResult {
        SUCCESS,
        TOO_SHORT,
        TOO_BIG
    }
}
