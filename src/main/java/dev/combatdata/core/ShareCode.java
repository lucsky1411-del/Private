package dev.combatdata.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A recording as one line of text to paste anywhere (Discord, chat apps, notes). It is the compressed recording file
 * in Base64; positions (the replay) are left out by default because they are most of the size.
 */
public final class ShareCode {
    public static final String PREFIX = "CDREC1:";

    private ShareCode() {
    }

    public static String encode(Recording r, boolean withPositions, String modVersion) throws IOException {
        Recording out = r;
        if (!withPositions) {
            List<Event> ev = new ArrayList<>(r.events().size());
            for (Event e : r.events()) if (!(e instanceof PositionEvent)) ev.add(e);
            out = new Recording(r.schema(), r.player(), r.playerUuid(), r.mcVersion(), r.wallStartMs(), r.durationMs(), ev,
                    r.notRecorded() | Features.POSITIONS);
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        Cdrec.writeTo(b, out, modVersion);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(b.toByteArray());
    }

    /** Finds a share code in any text (it may be wrapped in a message or a .txt file). */
    public static String find(String text) {
        if (text == null) return null;
        int i = text.indexOf(PREFIX);
        if (i < 0) return null;
        int j = i + PREFIX.length();
        while (j < text.length() && isCodeChar(text.charAt(j))) j++;
        return text.substring(i, j);
    }

    private static boolean isCodeChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    public static Recording decode(String text) throws IOException {
        String code = find(text);
        if (code == null) throw new IOException("no share code");
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new IOException("damaged share code", ex);
        }
        Recording r = Cdrec.readFrom(new ByteArrayInputStream(raw));
        if (r.events().isEmpty()) throw new IOException("empty recording");
        return r;
    }
}
