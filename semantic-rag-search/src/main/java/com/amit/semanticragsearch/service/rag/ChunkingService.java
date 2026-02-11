package com.amit.semanticragsearch.service.rag;

import com.amit.semanticragsearch.domain.Chunk;
import com.amit.semanticragsearch.domain.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;


@Service
public class ChunkingService {

    private final int chunkSize;
    private final int chunkOverlap;

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);
    private static final double CHARS_PER_TOKEN = 4.0;
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\n+");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("(?<=[.!?])\\s+");

    public ChunkingService(
            @Value("${app.chunking.chunk-size:400}") int chunkSize,
            @Value("${app.chunking.chunk-overlap:100}") int chunkOverlap) {

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than 0, got: " + chunkSize);
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("Chunk overlap must be >= 0, got: " + chunkOverlap);
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "Chunk overlap (%d) must be less than chunk size (%d)"
                            .formatted(chunkOverlap, chunkSize));
        }

        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;

        log.info("Initialized chunking service: chunkSize={} tokens, chunkOverlap={} tokens",
                chunkSize, chunkOverlap);
    }

    /**
     * Split a persisted document into chunks.
     * Returns chunks with IDs already set and embeddings unset.
     */
    public List<Chunk> chunkDocument(Document document) {
        if (document == null) 
            throw new IllegalArgumentException("Document cannot be null");
        
        if (document.getId() == null) 
            throw new IllegalArgumentException("Document ID cannot be null, document must be persisted before chunking");
        
        if (document.getTenantId() == null) 
            throw new IllegalArgumentException("Document tenantId cannot be null");
        
        if (document.getContent() == null || document.getContent().isBlank()) 
            throw new IllegalArgumentException("Document content cannot be null or blank");

        long startTime = System.currentTimeMillis();

        String content = document.getContent().replace("\r\n", "\n").replace("\r", "\n").strip();
        String[] paragraphs = PARAGRAPH_PATTERN.split(content);

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder currentChunkText = new StringBuilder();
        int currentTokenEstimate = 0;
        int chunkIndex = 0;

        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.strip();
            if (trimmedParagraph.isEmpty()) 
                continue;
            

            int paragraphTokens = estimateTokens(trimmedParagraph);

            // Handle oversized paragraphs first, otherwise overlap seeding can
            // create a duplicate overlap-only chunk.
            if (paragraphTokens > chunkSize) {
                if (currentTokenEstimate > 0) {
                    chunks.add(buildChunk(document, chunkIndex++, currentChunkText.toString().strip(), currentTokenEstimate));
                    currentChunkText = new StringBuilder();
                    currentTokenEstimate = 0;
                }

                List<Chunk> sentenceChunks = splitBySentences(
                        document, chunkIndex, trimmedParagraph);
                chunks.addAll(sentenceChunks);
                chunkIndex += sentenceChunks.size();
                continue;
            }

            if (currentTokenEstimate + paragraphTokens > chunkSize && currentTokenEstimate > 0) {
                chunks.add(buildChunk(document, chunkIndex++, currentChunkText.toString().strip(), currentTokenEstimate));

                String overlapText = extractOverlapText(currentChunkText.toString());
                currentChunkText = new StringBuilder(overlapText);
                currentTokenEstimate = estimateTokens(overlapText);
            }

            if (currentChunkText.length() > 0) 
                currentChunkText.append("\n\n");
            
            currentChunkText.append(trimmedParagraph);
            currentTokenEstimate += paragraphTokens;
        }


        if (currentTokenEstimate > 0) 
            chunks.add(buildChunk(document, chunkIndex, currentChunkText.toString().strip(), currentTokenEstimate));
        

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Document '{}' (id={}) split into {} chunks in {}ms, " +
                        "contentLength={} chars, avgChunkTokens={}",
                document.getTitle(), document.getId(), chunks.size(), elapsed,
                content.length(),
                chunks.isEmpty() ? 0 : chunks.stream()
                        .mapToInt(Chunk::getTokenCount)
                        .average()
                        .orElse(0));

        return chunks;
    }


    private List<Chunk> splitBySentences(Document document, int startIndex, String text) {
        List<Chunk> chunks = new ArrayList<>();
        String[] sentences = SENTENCE_PATTERN.split(text);

        StringBuilder currentChunkText = new StringBuilder();
        int currentTokenEstimate = 0;
        int chunkIndex = startIndex;

        for (String sentence : sentences) {
            String trimmedSentence = sentence.strip();
            if (trimmedSentence.isEmpty()) 
                continue;

            int sentenceTokens = estimateTokens(trimmedSentence);


            if (sentenceTokens > chunkSize) {
                if (currentTokenEstimate > 0) {
                    chunks.add(buildChunk(document, chunkIndex++, currentChunkText.toString().strip(), currentTokenEstimate));
                    currentChunkText = new StringBuilder();
                    currentTokenEstimate = 0;
                }

                List<Chunk> hardSplitChunks = hardSplitByTokenLimit(document, chunkIndex, trimmedSentence);
                chunks.addAll(hardSplitChunks);
                chunkIndex += hardSplitChunks.size();
                continue;
            }

            if (currentTokenEstimate + sentenceTokens > chunkSize && currentTokenEstimate > 0) {
                chunks.add(buildChunk(document, chunkIndex++, currentChunkText.toString().strip(), currentTokenEstimate));

                String overlapText = extractOverlapText(currentChunkText.toString());
                currentChunkText = new StringBuilder(overlapText);
                currentTokenEstimate = estimateTokens(overlapText);
            }

            if (currentChunkText.length() > 0) 
                currentChunkText.append(" ");
            
            currentChunkText.append(trimmedSentence);
            currentTokenEstimate += sentenceTokens;
        }

        if (currentTokenEstimate > 0) {
            chunks.add(buildChunk(document, chunkIndex,
                    currentChunkText.toString().strip(), currentTokenEstimate));
        }

        return chunks;
    }


    private List<Chunk> hardSplitByTokenLimit(Document document, int startIndex, String text) {
        List<Chunk> chunks = new ArrayList<>();
        int targetChars = (int) (chunkSize * CHARS_PER_TOKEN);
        int chunkIndex = startIndex;
        int position = 0;

        while (position < text.length()) {
            int end = Math.min(position + targetChars, text.length());

            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > position) 
                    end = lastSpace;
                
            }

            String segment = text.substring(position, end).strip();
            if (!segment.isEmpty()) 
                chunks.add(buildChunk(document, chunkIndex++, segment, estimateTokens(segment)));
            

            position = end;
            if (position < text.length() && text.charAt(position) == ' ') 
                position++;
        }

        log.warn("Hard-split oversized sentence into {} chunks for document {}",
                chunks.size(), document.getId());

        return chunks;
    }

    private String extractOverlapText(String text) {
        int targetChars = (int) (chunkOverlap * CHARS_PER_TOKEN);

        if (text.length() <= targetChars) 
            return text;
        

        String tail = text.substring(text.length() - targetChars);

        int sentenceBoundary = tail.indexOf(". ");
        if (sentenceBoundary > 0 && sentenceBoundary < tail.length() / 2) 
            return tail.substring(sentenceBoundary + 2);

        return tail;
    }


    private Chunk buildChunk(Document document, int chunkIndex, String content, int tokenCount) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.randomUUID());
        chunk.setDocumentId(document.getId());
        chunk.setTenantId(document.getTenantId());
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setTokenCount(tokenCount);
        return chunk;
    }


    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
