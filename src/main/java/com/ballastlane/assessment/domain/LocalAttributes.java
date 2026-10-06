package com.ballastlane.assessment.domain;

import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import java.util.List;
import java.util.Locale;

/** Proprietary fields that live only in our database; a re-sync from PokeAPI never touches them. */
public record LocalAttributes(String nickname, String region, List<String> tags) {

    public static final int MAX_TEXT_LENGTH = 50;
    public static final int MAX_TAGS = 10;
    public static final int MAX_TAG_LENGTH = 30;

    public LocalAttributes {
        nickname = cleanText(nickname, "nickname");
        region = cleanText(region, "region");
        tags = cleanTags(tags);
    }

    public static LocalAttributes empty() {
        return new LocalAttributes(null, null, List.of());
    }

    private static String cleanText(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.strip();
        if (clean.length() > MAX_TEXT_LENGTH) {
            throw new InvalidLocalAttributesException(field + " must have at most " + MAX_TEXT_LENGTH + " characters");
        }
        return clean;
    }

    private static List<String> cleanTags(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> tags = raw.stream().map(LocalAttributes::cleanTag).distinct().toList();
        if (tags.size() > MAX_TAGS) {
            throw new InvalidLocalAttributesException("At most " + MAX_TAGS + " tags are allowed");
        }
        return tags;
    }

    private static String cleanTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new InvalidLocalAttributesException("Tags cannot be blank");
        }
        String clean = tag.strip().toLowerCase(Locale.ROOT);
        if (clean.length() > MAX_TAG_LENGTH) {
            throw new InvalidLocalAttributesException("Tags must have at most " + MAX_TAG_LENGTH + " characters");
        }
        return clean;
    }
}
