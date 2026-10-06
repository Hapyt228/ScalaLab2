package org.example.gilb;

import org.example.gilb.GilbResult.ControlItem;
import org.example.gilb.GilbResult.StmtItem;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GilbAnalyzer {

    private record Tok(String text, int line) {}

    private static final class Block {
        final String type;
        final int depth;
        final int matchN;
        int caseIdx = 0;

        Block(String type, int depth, int matchN) {
            this.type = type;
            this.depth = depth;
            this.matchN = matchN;
        }
    }

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|=>|[{}()]");

    public GilbResult analyze(String source) {
        String clean = stripCommentsAndStrings(source);
        List<Tok> toks = tokenize(clean);

        List<ControlItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Deque<Block> stack = new ArrayDeque<>();

        int cl = 0;
        int maxDepth = 0;
        int parenDepth = 0;
        int lastIfDepth = 0;
        String pending = null;
        int pendingDepth = 0;
        int pendingN = 0;
        boolean awaitCond = false;
        boolean awaitBrace = false;
        boolean inCaseHeader = false;
        boolean afterElse = false;
        boolean doWhileTail = false;

        for (int i = 0; i < toks.size(); i++) {
            Tok t = toks.get(i);
            String s = t.text();

            if (doWhileTail) {
                doWhileTail = false;
                if (s.equals("while")) {
                    continue;
                }
            }

            if (awaitBrace && !s.equals("{") && !s.equals("yield")) {
                warnings.add("Строка " + t.line()
                        + ": тело оператора без фигурных скобок, вложенность может быть посчитана неточно");
                pending = null;
                awaitBrace = false;
            }

            if (s.equals("if") && !inCaseHeader) {
                int d = afterElse ? lastIfDepth + 1 : enclosing(stack) + 1;
                afterElse = false;
                cl++;
                maxDepth = Math.max(maxDepth, d);
                items.add(new ControlItem("if", t.line(), d, 1));
                pending = "IF";
                pendingDepth = d;
                awaitCond = true;
            } else if (s.equals("else")) {
                boolean elseIf = i + 1 < toks.size() && toks.get(i + 1).text().equals("if");
                if (elseIf) {
                    afterElse = true;
                } else {
                    pending = "ELSE";
                    pendingDepth = lastIfDepth;
                    awaitBrace = true;
                }
            } else if (s.equals("while") || s.equals("for")) {
                int d = enclosing(stack) + 1;
                cl++;
                maxDepth = Math.max(maxDepth, d);
                items.add(new ControlItem(s, t.line(), d, 1));
                pending = s.toUpperCase();
                pendingDepth = d;
                awaitCond = true;
            } else if (s.equals("do")) {
                int d = enclosing(stack) + 1;
                cl++;
                maxDepth = Math.max(maxDepth, d);
                items.add(new ControlItem("do-while", t.line(), d, 1));
                pending = "DO";
                pendingDepth = d;
                awaitBrace = true;
            } else if (s.equals("match")) {
                int n = countCases(toks, i + 1);
                int d = enclosing(stack) + 1;
                int ifs = Math.max(n - 1, 0);
                cl += ifs;
                if (ifs > 0) {
                    maxDepth = Math.max(maxDepth, d + ifs - 1);
                }
                items.add(new ControlItem("match (" + n + " ветв.)", t.line(), d, ifs));
                pending = "MATCH";
                pendingDepth = d;
                pendingN = n;
                awaitBrace = true;
            } else if (s.equals("yield")) {
                if (pending != null && awaitBrace) {
                    boolean braced = i + 1 < toks.size() && toks.get(i + 1).text().equals("{");
                    if (!braced) {
                        pending = null;
                        awaitBrace = false;
                    }
                }
            } else if (s.equals("case")) {
                if (!stack.isEmpty() && stack.peek().type.equals("MATCH")) {
                    stack.peek().caseIdx++;
                    inCaseHeader = true;
                }
            } else if (s.equals("=>")) {
                inCaseHeader = false;
            } else if (s.equals("(")) {
                parenDepth++;
            } else if (s.equals(")")) {
                parenDepth--;
                if (parenDepth == 0 && awaitCond) {
                    awaitCond = false;
                    awaitBrace = true;
                }
            } else if (s.equals("{")) {
                if (pending != null && awaitBrace) {
                    stack.push(new Block(pending, pendingDepth, pendingN));
                    pending = null;
                    awaitBrace = false;
                } else {
                    stack.push(new Block("PLAIN", 0, 0));
                }
            } else if (s.equals("}")) {
                if (!stack.isEmpty()) {
                    Block b = stack.pop();
                    if (b.type.equals("IF") || b.type.equals("ELSE")) {
                        lastIfDepth = b.depth;
                    } else if (b.type.equals("DO")) {
                        doWhileTail = true;
                    }
                }
            }
        }

        if (!stack.isEmpty()) {
            warnings.add("Не сбалансированы фигурные скобки: осталось незакрытых блоков " + stack.size());
        }

        List<StmtItem> stmts = countStatements(clean);
        int n = stmts.size();
        double rel = n == 0 ? 0.0 : (double) cl / n;
        return new GilbResult(cl, n, rel, maxDepth, items, stmts, warnings);
    }

    private int enclosing(Deque<Block> stack) {
        for (Block b : stack) {
            switch (b.type) {
                case "IF", "ELSE", "WHILE", "FOR", "DO" -> {
                    return b.depth;
                }
                case "MATCH" -> {
                    int cap = Math.max(b.matchN - 1, 0);
                    return b.depth - 1 + Math.min(b.caseIdx, cap);
                }
                default -> { }
            }
        }
        return -1;
    }

    private int countCases(List<Tok> toks, int from) {
        if (from >= toks.size() || !toks.get(from).text().equals("{")) {
            return 0;
        }
        int depth = 0;
        int count = 0;
        for (int j = from; j < toks.size(); j++) {
            String s = toks.get(j).text();
            if (s.equals("{")) {
                depth++;
            } else if (s.equals("}")) {
                depth--;
                if (depth == 0) {
                    break;
                }
            } else if (s.equals("case") && depth == 1) {
                boolean decl = j + 1 < toks.size()
                        && (toks.get(j + 1).text().equals("class") || toks.get(j + 1).text().equals("object"));
                if (!decl) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<StmtItem> countStatements(String clean) {
        List<StmtItem> res = new ArrayList<>();
        String[] lines = clean.split("\n", -1);
        for (int idx = 0; idx < lines.length; idx++) {
            String line = lines[idx].strip();
            boolean closed = false;
            while (line.startsWith("}")) {
                closed = true;
                line = line.substring(1).strip();
            }
            if (line.isEmpty() || line.equals("{")) {
                continue;
            }
            if (line.matches("^else\\b.*")) {
                String rest = line.substring(4).strip();
                if (rest.matches("^if\\b.*")) {
                    res.add(new StmtItem(idx + 1, rest));
                }
                continue;
            }
            if (closed && line.matches("^while\\b.*")) {
                continue;
            }
            if (line.matches("^(import|package|object|class|trait|def|case\\s+class)\\b.*")) {
                continue;
            }
            if (line.matches("^case\\b.*")) {
                int arrow = line.indexOf("=>");
                if (arrow < 0) {
                    continue;
                }
                line = line.substring(arrow + 2).strip();
                if (line.isEmpty() || line.equals("{")) {
                    continue;
                }
            }
            for (String part : line.split(";")) {
                String p = part.strip();
                if (!p.isEmpty() && !p.equals("{") && !p.equals("}")) {
                    res.add(new StmtItem(idx + 1, p));
                }
            }
        }
        return res;
    }

    private List<Tok> tokenize(String clean) {
        List<Tok> toks = new ArrayList<>();
        Matcher m = TOKEN.matcher(clean);
        int line = 1;
        int last = 0;
        while (m.find()) {
            for (int k = last; k < m.start(); k++) {
                if (clean.charAt(k) == '\n') {
                    line++;
                }
            }
            last = m.start();
            toks.add(new Tok(m.group(), line));
        }
        return toks;
    }

    private String stripCommentsAndStrings(String src) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        sb.append('\n');
                    }
                    i++;
                }
                i += 2;
                continue;
            }
            if (c == '"') {
                if (src.startsWith("\"\"\"", i)) {
                    i += 3;
                    while (i < n && !src.startsWith("\"\"\"", i)) {
                        if (src.charAt(i) == '\n') {
                            sb.append('\n');
                        }
                        i++;
                    }
                    i += 3;
                } else {
                    i++;
                    while (i < n && src.charAt(i) != '"') {
                        if (src.charAt(i) == '\\') {
                            i++;
                        }
                        i++;
                    }
                    i++;
                }
                sb.append("\"\"");
                continue;
            }
            if (c == '\'' && i + 2 < n) {
                if (src.charAt(i + 1) == '\\' && i + 3 < n && src.charAt(i + 3) == '\'') {
                    i += 4;
                    sb.append("' '");
                    continue;
                }
                if (src.charAt(i + 2) == '\'') {
                    i += 3;
                    sb.append("' '");
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}