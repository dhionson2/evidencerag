package dev.evidencerag.application;

import dev.evidencerag.domain.Chunk;
import dev.evidencerag.domain.ExtractedPage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentChunkingService {
    public List<Chunk> chunk(List<ExtractedPage> pages, int chunkSize, int overlap) {
        if (chunkSize < 1 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("chunk-size deve ser positivo e overlap menor que chunk-size");
        }
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (ExtractedPage page : pages) {
            String source = page.text() == null ? "" : page.text().replace("\r\n", "\n").replace('\r', '\n');
            int cursor = 0;
            String heading = null;
            while (cursor < source.length()) {
                while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
                if (cursor >= source.length()) break;
                int start = cursor;
                int end = nextParagraphEnd(source, start);
                while (end > start && Character.isWhitespace(source.charAt(end - 1))) end--;
                if (end <= start) { cursor = Math.max(start + 1, end + 1); continue; }
                String block = source.substring(start, end).trim();
                if (block.startsWith("#")) {
                    heading = block;
                    cursor = nextParagraphStart(source, end);
                    continue;
                }
                String context = heading == null ? "" : heading + "\n";
                String clean = block;
                int bodyCursor = 0;
                while (bodyCursor < clean.length()) {
                    int localEnd = Math.min(clean.length(), bodyCursor + Math.max(1, chunkSize - context.length()));
                    if (localEnd < clean.length()) {
                        int boundary = clean.lastIndexOf(' ', localEnd);
                        if (boundary > bodyCursor + Math.max(1, chunkSize - context.length()) / 2) localEnd = boundary;
                    }
                    String content = (context + clean.substring(bodyCursor, localEnd).replaceAll("\\s+", " ")).trim();
                    if (!content.isBlank()) {
                        int sourceStart = start + bodyCursor;
                        int sourceEnd = Math.min(end, sourceStart + Math.max(0, localEnd - bodyCursor));
                        chunks.add(new Chunk(UUID.randomUUID(), index++, page.pageNumber(), sourceStart, sourceEnd, content));
                    }
                    if (localEnd >= clean.length()) break;
                    bodyCursor = Math.max(bodyCursor + 1, localEnd - overlap);
                    context = heading == null ? "" : heading + "\n";
                }
                heading = null;
                cursor = nextParagraphStart(source, end);
            }
        }
        return List.copyOf(chunks);
    }

    private int nextParagraphEnd(String source, int start) {
        int boundary = source.indexOf("\n\n", start);
        return boundary < 0 ? source.length() : boundary;
    }

    private int nextParagraphStart(String source, int end) {
        int next = source.indexOf("\n\n", end);
        return next < 0 ? source.length() : next + 2;
    }
}
