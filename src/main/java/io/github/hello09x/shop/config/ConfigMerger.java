package io.github.hello09x.shop.config;

import io.github.hello09x.shop.ShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置自动补全。
 *
 * <p>做法是<b>纯文本行级合并</b>，而不是读成一个 YamlConfiguration 再写回去 ——
 * 后者会丢掉所有注释、打乱键顺序。</p>
 *
 * <p>规则：</p>
 * <ul>
 *     <li>以 jar 内置的默认文件为「模板」</li>
 *     <li>模板里的每个键，如果用户文件里没有，就<b>连同它的注释块</b>一起补到合适位置</li>
 *     <li>用户已经写过的键，值<b>一字不改</b></li>
 *     <li>补全前先写一个 {@code .bak} 备份</li>
 *     <li>补全后校验 YAML 合法性，不合法就回滚</li>
 * </ul>
 */
public final class ConfigMerger {

    private final ShopPlugin plugin;

    public ConfigMerger(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** 补全结果 */
    public record Result(boolean changed, int added, @Nullable String error) {
        public static Result unchanged() {
            return new Result(false, 0, null);
        }

        public static Result added(int n) {
            return new Result(true, n, null);
        }

        public static Result failed(@NotNull String message) {
            return new Result(false, 0, message);
        }
    }

    /**
     * 补全插件数据目录下的某个文件
     *
     * @param relativePath 相对插件数据目录的路径，例如 {@code config.yml}、{@code shops/购买-基础物资.yml}
     */
    public @NotNull Result merge(@NotNull String relativePath) {
        File userFile = new File(plugin.getDataFolder(), relativePath);
        if (!userFile.exists()) {
            // 还不存在：直接从 jar 释放
            try {
                plugin.saveResource(relativePath, false);
                return Result.added(-1);
            } catch (Throwable t) {
                return Result.failed("释放 " + relativePath + " 失败：" + t.getMessage());
            }
        }

        List<String> template;
        try (InputStream in = plugin.getResource(relativePath)) {
            if (in == null) {
                return Result.unchanged();
            }
            template = readLines(in);
        } catch (Exception e) {
            return Result.failed("读取内置模板失败：" + e.getMessage());
        }

        List<String> current;
        try {
            current = Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Result.failed("读取用户文件失败：" + e.getMessage());
        }

        MergeOutcome outcome = mergeLines(current, template);
        if (!outcome.changed()) {
            return Result.unchanged();
        }

        // 备份
        try {
            Path backup = userFile.toPath().resolveSibling(userFile.getName() + ".bak");
            Files.write(backup, current, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Result.failed("备份失败：" + e.getMessage());
        }

        // 写回
        try {
            Path tmp = userFile.toPath().resolveSibling(userFile.getName() + ".tmp");
            Files.write(tmp, outcome.lines(), StandardCharsets.UTF_8);
            // 校验
            YamlConfiguration.loadConfiguration(tmp.toFile());
            Files.move(tmp, userFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            userFile.delete();
            try {
                Files.write(userFile.toPath(), current, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // 回滚也失败就只能让用户从 .bak 恢复了
            }
            return Result.failed("写入失败（已回滚）：" + e.getMessage());
        }

        return Result.added(outcome.added());
    }

    /**
     * 把模板里缺失的键补进用户文件
     */
    private @NotNull MergeOutcome mergeLines(@NotNull List<String> user, @NotNull List<String> template) {
        Map<String, Integer> userKeys = indexKeys(user);
        List<String> result = new ArrayList<>(user);

        List<TemplateBlock> blocks = collectBlocks(template);
        List<String> insertedPaths = new ArrayList<>();
        int added = 0;

        for (TemplateBlock block : blocks) {
            if (userKeys.containsKey(block.key())) {
                continue;
            }
            // 父节点不存在（且不是本次刚补进去的）就跳过
            if (block.parent() != null
                    && !userKeys.containsKey(block.parent())
                    && !insertedPaths.contains(block.parent())) {
                continue;
            }
            int at = findInsertPosition(result, block);
            result.addAll(at, block.toLines());
            added++;
            insertedPaths.add(block.key());
            userKeys.put(block.key(), at);
        }

        return new MergeOutcome(added > 0, result, added);
    }

    /**
     * 找出应该插入的位置：同级最后一个键之后
     */
    private int findInsertPosition(@NotNull List<String> lines, @NotNull TemplateBlock block) {
        int indent = block.indent();
        int lastAtLevel = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) {
                continue;
            }
            int cur = indentOf(line);
            if (cur == indent && isKeyLine(line)) {
                lastAtLevel = i;
            }
        }
        if (lastAtLevel < 0) {
            return lines.size();
        }
        // 插到该键所属块的末尾（跳过它下面的所有缩进内容）
        int pos = lastAtLevel + 1;
        while (pos < lines.size()) {
            String line = lines.get(pos);
            if (isBlankOrComment(line)) {
                pos++;
                continue;
            }
            if (indentOf(line) <= indent) {
                break;
            }
            pos++;
        }
        return pos;
    }

    /**
     * 从模板里提取「键 + 前置注释 + 子内容」块
     */
    private @NotNull List<TemplateBlock> collectBlocks(@NotNull List<String> template) {
        List<TemplateBlock> blocks = new ArrayList<>();
        List<String> pendingComments = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        List<Integer> stackIndents = new ArrayList<>();

        for (int i = 0; i < template.size(); i++) {
            String line = template.get(i);

            if (isBlankOrComment(line)) {
                pendingComments.add(line);
                continue;
            }
            if (!isKeyLine(line)) {
                pendingComments.clear();
                continue;
            }

            int indent = indentOf(line);
            String key = keyOf(line);
            String value = valueOf(line);

            // 维护父路径栈
            while (!stackIndents.isEmpty() && indent <= stackIndents.get(stackIndents.size() - 1)) {
                stackIndents.remove(stackIndents.size() - 1);
                stack.remove(stack.size() - 1);
            }
            String parent = stack.isEmpty() ? null : String.join(".", stack);
            String fullPath = parent == null ? key : parent + "." + key;

            // 收集这个键下面的所有子行（缩进更深的行）
            List<String> body = new ArrayList<>();
            int j = i + 1;
            while (j < template.size()) {
                String next = template.get(j);
                if (isBlankOrComment(next) || indentOf(next) > indent) {
                    body.add(next);
                    j++;
                    continue;
                }
                break;
            }
            // 去掉 body 末尾多余空行
            while (!body.isEmpty() && body.get(body.size() - 1).trim().isEmpty()) {
                body.remove(body.size() - 1);
            }

            blocks.add(new TemplateBlock(
                    fullPath, parent, indent,
                    // 文件头注释（第一个键之前的那一大段）不带走，
                    // 否则补一个键就会把整个文件头粘到文件中间
                    i == 0 || pendingComments.isEmpty()
                            ? new ArrayList<>()
                            : trimHeaderComment(new ArrayList<>(pendingComments)),
                    line, body
            ));
            pendingComments.clear();

            if (value.isEmpty()) {
                stack.add(key);
                stackIndents.add(indent);
            }

            i = j - 1;
        }
        return blocks;
    }

    /**
     * 去掉注释块里属于「文件头」的部分。
     *
     * <p>约定：注释块里如果出现 <code>====</code> 分隔线，说明这是模板开头的
     * 说明性大段注释，不随键迁移；只保留分隔线之后的部分。</p>
     */
    private static @NotNull List<String> trimHeaderComment(@NotNull List<String> comments) {
        int lastBar = -1;
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).contains("====")) {
                lastBar = i;
            }
        }
        if (lastBar < 0) {
            return comments;
        }
        List<String> tail = new ArrayList<>(comments.subList(lastBar + 1, comments.size()));
        // 去掉紧跟分隔线的空行
        while (!tail.isEmpty() && tail.get(0).trim().isEmpty()) {
            tail.remove(0);
        }
        return tail;
    }

    // ------------------------------------------------------------ 小工具

    private static @NotNull Map<String, Integer> indexKeys(@NotNull List<String> lines) {
        Map<String, Integer> map = new LinkedHashMap<>();
        List<String> stack = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line) || !isKeyLine(line)) {
                continue;
            }
            int indent = indentOf(line);
            // 弹栈：同级或更浅的键出现时，把更深的父路径清掉
            while (!indents.isEmpty() && indent <= indents.get(indents.size() - 1)) {
                indents.remove(indents.size() - 1);
                stack.remove(stack.size() - 1);
            }
            String key = keyOf(line);
            String path = stack.isEmpty() ? key : String.join(".", stack) + "." + key;
            map.put(path, i);

            // 顶层键也记录一次它的短名，避免「父节点已被补充过」时误判
            if (stack.isEmpty()) {
                map.putIfAbsent(key, i);
            }

            if (valueOf(line).isEmpty()) {
                stack.add(key);
                indents.add(indent);
            }
        }
        return map;
    }

    private static boolean isBlankOrComment(@NotNull String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith("#");
    }

    private static boolean isKeyLine(@NotNull String line) {
        String t = line.trim();
        if (t.startsWith("-")) {
            return false;
        }
        int colon = t.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String key = t.substring(0, colon).trim();
        return !key.isEmpty() && !key.contains(" ");
    }

    private static int indentOf(@NotNull String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static @NotNull String keyOf(@NotNull String line) {
        String t = line.trim();
        return t.substring(0, t.indexOf(':')).trim();
    }

    private static @NotNull String valueOf(@NotNull String line) {
        String t = line.trim();
        int colon = t.indexOf(':');
        if (colon < 0) {
            return "";
        }
        String v = t.substring(colon + 1).trim();
        // 去掉行尾注释
        int hash = v.indexOf(" #");
        if (hash >= 0) {
            v = v.substring(0, hash).trim();
        }
        return v;
    }

    private static @NotNull List<String> readLines(@NotNull InputStream in) throws Exception {
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            var br = new java.io.BufferedReader(reader);
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        }
    }

    private record MergeOutcome(boolean changed, List<String> lines, int added) {
    }

    private record TemplateBlock(
            @NotNull String key,
            @Nullable String parent,
            int indent,
            @NotNull List<String> comments,
            @NotNull String keyLine,
            @NotNull List<String> body
    ) {
        List<String> toLines() {
            List<String> out = new ArrayList<>(comments);
            out.add(keyLine);
            out.addAll(body);
            return out;
        }
    }

}
