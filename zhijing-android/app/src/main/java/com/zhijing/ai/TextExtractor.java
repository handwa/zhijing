package com.zhijing.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextExtractor {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "看", "好",
            "自己", "这", "那", "但",
            "from", "the", "a", "an", "is", "are", "was", "were",
            "it", "this", "that", "with", "for", "on", "at", "to", "and", "or", "but"
    ));

    private static final Pattern BOOK_PATTERN = Pattern.compile("\u300a([^\u300b]{2,20})\u300b");
    private static final Pattern PERSON_PATTERN = Pattern.compile("[A-Z][a-z]+ [A-Z][a-z]+|[\u4e00-\u9fa5]{2,4}(?:\u535a\u58eb|\u6559\u6388|\u9662\u58eb|\u5148\u751f|\u5973\u58eb)");

    public static class ExtractionResult {
        public List<String> keywords = new ArrayList<>();
        public List<String> entities = new ArrayList<>();
        public List<String> entityTypes = new ArrayList<>();
    }

    public ExtractionResult extract(String text) {
        ExtractionResult result = new ExtractionResult();
        if (text == null || text.trim().isEmpty()) return result;
        extractEntities(text, result);
        extractKeywords(text, result);
        return result;
    }

    private void extractEntities(String text, ExtractionResult result) {
        Matcher bookMatcher = BOOK_PATTERN.matcher(text);
        while (bookMatcher.find()) {
            result.entities.add(bookMatcher.group(1));
            result.entityTypes.add("WORK");
        }
        Matcher personMatcher = PERSON_PATTERN.matcher(text);
        while (personMatcher.find()) {
            result.entities.add(personMatcher.group());
            result.entityTypes.add("PERSON");
        }
        Pattern properNounPattern = Pattern.compile("\\b([A-Z][a-z]*(?:\\s+[A-Z][a-z]*){1,4})\\b");
        Matcher properNounMatcher = properNounPattern.matcher(text);
        while (properNounMatcher.find()) {
            String term = properNounMatcher.group();
            if (term.split("\\s+").length >= 2) {
                result.entities.add(term);
                result.entityTypes.add("CONCEPT");
            }
        }
    }

    private void extractKeywords(String text, ExtractionResult result) {
        String[] tokens = text.split("[\\s\\p{P}\\uff00-\\uffef]+");
        Set<String> seen = new HashSet<>();
        for (String token : tokens) {
            String cleaned = token.trim();
            if (cleaned.length() >= 2 && !STOP_WORDS.contains(cleaned) && !seen.contains(cleaned)) {
                result.keywords.add(cleaned);
                seen.add(cleaned);
                if (result.keywords.size() >= 20) break;
            }
        }
    }
}
