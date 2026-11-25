package dev.evidencerag.application;

import dev.evidencerag.domain.Citation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CitationValidationService {
    private static final Pattern CITATION = Pattern.compile("\\[chunk:([0-9a-fA-F-]{36})]", Pattern.CASE_INSENSITIVE);

    public List<Citation> citedOnly(String answer, List<Citation> retrieved) {
        Set<UUID> available = retrieved.stream().map(Citation::chunkId).collect(Collectors.toSet());
        java.util.LinkedHashSet<UUID> cited = new java.util.LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            try {
                UUID id = UUID.fromString(matcher.group(1));
                if (!available.contains(id)) return List.of();
                cited.add(id);
            } catch (IllegalArgumentException ignored) { return List.of(); }
        }
        return retrieved.stream().filter(c -> cited.contains(c.chunkId())).toList();
    }

    /** Resolves model-provided structured identifiers only against this request's retrieved evidence. */
    public List<Citation> retrievedOnly(List<String> evidenceIds, List<Citation> retrieved) {
        if (evidenceIds == null || evidenceIds.isEmpty()) return List.of();
        Set<UUID> available = retrieved.stream().map(Citation::chunkId).collect(Collectors.toSet());
        java.util.LinkedHashSet<UUID> requested = new java.util.LinkedHashSet<>();
        try {
            for (String id : evidenceIds) requested.add(UUID.fromString(id));
        } catch (RuntimeException ex) {
            return List.of();
        }
        if (!available.containsAll(requested)) return List.of();
        return retrieved.stream().filter(c -> requested.contains(c.chunkId())).toList();
    }
}
