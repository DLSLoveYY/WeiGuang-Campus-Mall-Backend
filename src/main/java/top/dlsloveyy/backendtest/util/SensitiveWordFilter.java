package top.dlsloveyy.backendtest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);
    private static final TrieNode ROOT = new TrieNode();

    static {
        loadDictionary();
    }

    private SensitiveWordFilter() {
    }

    private static void loadDictionary() {
        InputStream input = SensitiveWordFilter.class.getResourceAsStream("/sensitive-words.txt");
        if (input == null) {
            log.warn("sensitive-words.txt 未找到，敏感词过滤将不可用");
            return;
        }

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = normalize(line);
                if (word.isEmpty()) continue;
                addWord(word);
                count++;
            }
            log.info("敏感词词库加载完成，词条数量: {}", count);
        } catch (Exception e) {
            log.error("敏感词词库加载失败", e);
        }
    }

    private static void addWord(String word) {
        TrieNode node = ROOT;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.end = true;
        node.word = word;
    }

    public static boolean contains(String text) {
        return !findAll(text).isEmpty();
    }

    public static List<String> findAll(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) return List.of();

        Set<String> hits = new HashSet<>();
        int n = normalized.length();

        for (int start = 0; start < n; start++) {
            TrieNode state = ROOT;
            for (int index = start; index < n; index++) {
                char c = normalized.charAt(index);
                state = state.children.get(c);
                if (state == null) break;
                if (state.end && state.word != null) {
                    hits.add(state.word);
                }
            }
        }

        return new ArrayList<>(hits);
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) return "";

        StringBuilder sb = new StringBuilder();
        String lower = text.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (Character.isLetterOrDigit(c) || isCjk(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean end;
        private String word;
    }
}
