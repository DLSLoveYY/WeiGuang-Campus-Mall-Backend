package top.dlsloveyy.backendtest.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SensitiveWordFilter {

    private static final Map<Character, Node> root = new HashMap<>();

    static {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                SensitiveWordFilter.class.getResourceAsStream("/sensitive-words.txt"), StandardCharsets.UTF_8))) {
            String word;
            while ((word = reader.readLine()) != null) {
                addWord(word.trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addWord(String word) {
        if (word == null || word.isEmpty()) return;  // ✅ 防止空词条误判
        Map<Character, Node> current = root;
        for (char c : word.toCharArray()) {
            Node next = current.get(c);
            if (next == null) {
                next = new Node();
                current.put(c, next);
            }
            current = next.children;
        }
        current.put('\0', new Node()); // 结束符
    }


    public static boolean contains(String text) {
        if (text == null) return false;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            Map<Character, Node> currentMap = root;
            for (int j = i; j < chars.length; j++) {
                char c = chars[j];
                Node node = currentMap.get(c);
                if (node == null) break;
                if (node.children.containsKey('\0')) return true; // 匹配到完整词
                currentMap = node.children;
            }
        }
        return false;
    }

    private static class Node {
        Map<Character, Node> children = new HashMap<>();
    }
}
