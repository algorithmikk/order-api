package com.umameats.support.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.chat.model.ChatRole;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keyword search over the bundled help centre.
 *
 * <p>Grounding matters more than retrieval sophistication here. The corpus is a
 * few dozen short articles, so scored keyword overlap finds the right one and
 * costs nothing; a vector store would add a dependency and a bill to solve a
 * problem this size does not have.
 */
@Slf4j
@Component
public class HelpCenterIndex {

    private static final String RESOURCE_PATH = "support/help-center.json";

    /** Words too common to discriminate between articles. */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "do", "does", "did", "how", "what", "why",
            "when", "where", "can", "i", "my", "me", "to", "of", "for", "in", "on", "and", "or",
            "it", "this", "that", "with", "have", "has", "get", "je", "le", "la", "les", "un", "une",
            "de", "des", "du", "et", "ou", "mon", "ma", "mes", "est", "sont", "comment", "pourquoi",
            "quand", "pour", "dans", "sur", "avec");

    private static final int TITLE_WEIGHT = 3;
    private static final int TAG_WEIGHT = 2;

    private List<HelpCenterArticle> articles = List.of();

    @PostConstruct
    void load() {
        try (InputStream stream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            articles = new ObjectMapper().readValue(stream, new TypeReference<List<HelpCenterArticle>>() {
            });
            log.info("Loaded {} help centre articles", articles.size());
        } catch (Exception e) {
            // A missing corpus degrades answer quality but must not stop the
            // service; the agent still has its live order tools.
            log.error("Could not load {}: {}", RESOURCE_PATH, e.getMessage());
            articles = List.of();
        }
    }

    /**
     * @return the best-matching articles for the audience and language, or an
     *         empty list when nothing scores above zero
     */
    public List<HelpCenterArticle> search(String query, ChatRole role, String locale, int limit) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty() || articles.isEmpty()) {
            return List.of();
        }

        String audience = role == ChatRole.DRIVER ? "DRIVER" : "CUSTOMER";
        String language = locale != null ? locale : "en";

        List<ScoredArticle> scored = new ArrayList<>();
        for (HelpCenterArticle article : articles) {
            if (!audience.equals(article.audience()) && !"ALL".equals(article.audience())) {
                continue;
            }
            if (!language.equals(article.locale())) {
                continue;
            }
            int score = score(article, queryTerms);
            if (score > 0) {
                scored.add(new ScoredArticle(article, score));
            }
        }

        scored.sort(Comparator.comparingInt(ScoredArticle::score).reversed());
        return scored.stream().limit(limit).map(ScoredArticle::article).toList();
    }

    private int score(HelpCenterArticle article, Set<String> queryTerms) {
        Set<String> titleTerms = tokenize(article.question());
        Set<String> bodyTerms = tokenize(article.answer());
        Set<String> tagTerms = new HashSet<>();
        if (article.tags() != null) {
            article.tags().forEach(tag -> tagTerms.addAll(tokenize(tag)));
        }

        int score = 0;
        for (String term : queryTerms) {
            if (titleTerms.contains(term)) score += TITLE_WEIGHT;
            if (tagTerms.contains(term)) score += TAG_WEIGHT;
            if (bodyTerms.contains(term)) score += 1;
        }
        return score;
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() > 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(java.util.stream.Collectors.toSet());
    }

    private record ScoredArticle(HelpCenterArticle article, int score) {
    }
}
