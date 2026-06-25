package com.org.ai.agents.codeassistant.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.ai.agents.codeassistant.config.CodeAssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.org.ai.agents.codeassistant.permission.PermissionEvaluator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ProjectCodeTool {

    private static final Logger logger = LoggerFactory.getLogger(ProjectCodeTool.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CodeAssistantProperties properties;
    private final PermissionEvaluator permissionEvaluator;

    public ProjectCodeTool(CodeAssistantProperties properties, PermissionEvaluator permissionEvaluator) {
        this.properties = properties;
        this.permissionEvaluator = permissionEvaluator;
    }

    // 允许执行的命令白名单（可配置，但保持默认）
    private static final List<String> ALLOWED_COMMAND_PREFIXES = List.of(
            "mvn compile",
            "mvn test",
            "mvn clean",
            "git status",
            "git diff",
            "git checkout -- ",
            "git log",
            "git stash",
            "git add ",
            "git reset ",
            "git rev-parse",
            "git branch",
            "git pull --ff-only"
    );

    // ----------------------------------- 公共工具方法 -----------------------------------

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 解析并规范化工作空间内的绝对路径，同时防止目录遍历攻击。
     */
    private Path resolvePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        Path workspacePath = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Path target = workspacePath.resolve(relativePath).normalize();
        if (!target.startsWith(workspacePath)) {
            throw new SecurityException("非法访问路径：" + target);
        }
        return target;
    }

    /**
     * 获取工作空间的绝对路径。
     */
    private Path getWorkspacePath() {
        return Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
    }

    /**
     * 生成统一格式的错误 JSON 响应。
     */
    private String errorResponse(String message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("success", false, "error", message));
        } catch (JsonProcessingException e) {
            // 极少数情况，回退到手动字符串
            return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
        }
    }

    /**
     * 简易 JSON 转义（仅用于回退）。
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 计算 Unified diff（如果文件已存在）。使用 delta 信息手动构建。
     */
    private String computeDiff(Path path, String newContent, String relativePath) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            List<String> original = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> revised = Arrays.asList(newContent.split("\n", -1));
            Patch<String> patch = DiffUtils.diff(original, revised);
            if (patch.getDeltas().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("--- a/").append(relativePath).append("\n");
            sb.append("+++ b/").append(relativePath).append("\n");
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                int srcPos = delta.getSource().getPosition() + 1;
                int srcLines = delta.getSource().getLines().size();
                int tgtPos = delta.getTarget().getPosition() + 1;
                int tgtLines = delta.getTarget().getLines().size();
                sb.append("@@ -").append(srcPos).append(",").append(srcLines)
                        .append(" +").append(tgtPos).append(",").append(tgtLines).append(" @@\n");
                if (delta.getType() == DeltaType.DELETE || delta.getType() == DeltaType.CHANGE) {
                    for (String line : delta.getSource().getLines()) {
                        sb.append("-").append(line).append("\n");
                    }
                }
                if (delta.getType() == DeltaType.INSERT || delta.getType() == DeltaType.CHANGE) {
                    for (String line : delta.getTarget().getLines()) {
                        sb.append("+").append(line).append("\n");
                    }
                }
            }
            return sb.toString();
        } catch (IOException e) {
            logger.warn("计算 diff 失败：{}", relativePath, e);
            return "";
        }
    }

    private String checkPermission(String toolName, String arg) {
        PermissionEvaluator.Result result = permissionEvaluator.check(toolName, arg);
        if (result == PermissionEvaluator.Result.DENY) {
            return "错误：操作已被管理员禁止（" + toolName + "）";
        }
        if (result == PermissionEvaluator.Result.ASK) {
            return "错误：操作需要用户确认（" + toolName + "），请先征得用户同意后再执行";
        }
        return null;
    }

    // ----------------------------------- 工具方法（暴露给 AI） -----------------------------------

    /**
     * 读取文件内容。
     */
    @Tool(description = "读取工作空间内指定路径的文件内容。路径相对于工作空间根目录。")
    public String readFile(
            @ToolParam(description = "文件相对路径，如 src/main/java/App.java") String relativePath
    ) {
        try {
            Path path = resolvePath(relativePath);
            if (!Files.isRegularFile(path)) {
                return "错误：路径不是常规文件：" + relativePath;
            }
            long size = Files.size(path);
            if (size > properties.getMaxFileSizeBytes()) {
                return "错误：文件大小 " + size + " 字节超过上限 " + properties.getMaxFileSizeBytes() + " 字节";
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            logger.info("读取文件成功：{}", relativePath);
            return content;
        } catch (SecurityException | IllegalArgumentException e) {
            logger.warn("读取文件安全异常：{}", e.getMessage());
            return "错误：" + e.getMessage();
        } catch (IOException e) {
            logger.error("读取文件失败：{}", relativePath, e);
            return "错误：读取文件失败 - " + e.getMessage();
        }
    }

    /**
     * 列出目录下的文件（相对路径）。
     */
    @Tool(description = "列出指定目录下的所有文件和文件夹（相对路径），用于了解项目结构。")
    public List<String> listDirectory(
            @ToolParam(description = "目录相对路径，默认为空（根目录）") String relativePath
    ) {
        try {
            String dir = (relativePath == null || relativePath.isBlank()) ? "." : relativePath;
            Path path = resolvePath(dir);
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("路径不是目录：" + dir);
            }
            Path workspacePath = getWorkspacePath();
            try (Stream<Path> stream = Files.list(path)) {
                return stream
                        .map(p -> workspacePath.relativize(p.toAbsolutePath()).toString())
                        .map(p -> p.replace(File.separatorChar, '/')) // 统一使用 /
                        .filter(p -> !p.isEmpty())
                        .collect(Collectors.toList());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            logger.warn("列出目录安全异常：{}", e.getMessage());
            return List.of("错误：" + e.getMessage());
        } catch (IOException e) {
            logger.error("列出目录失败：{}", relativePath, e);
            return List.of("错误：列出目录失败 - " + e.getMessage());
        }
    }

    /**
     * 根据文件名关键词查找文件（支持深度限制）。
     */
    @Tool(description = "根据文件名关键词查找工作空间中的文件，支持最大搜索深度。例如查找 'UserService'。")
    public List<String> findFiles(
            @ToolParam(description = "文件名关键词（必填）") String keyword,
            @ToolParam(description = "最大搜索深度（可选，默认 5）") Integer maxDepth
    ) {
        try {
            if (keyword == null || keyword.isBlank()) {
                return List.of("错误：关键词不能为空");
            }
            int depth = (maxDepth != null && maxDepth > 0) ? maxDepth : properties.getMaxSearchDepth();
            Path workspacePath = getWorkspacePath();
            try (Stream<Path> stream = Files.walk(workspacePath, depth)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().contains(keyword))
                        .map(p -> workspacePath.relativize(p).toString())
                        .map(p -> p.replace(File.separatorChar, '/'))
                        .collect(Collectors.toList());
            }
        } catch (SecurityException e) {
            logger.warn("查找文件安全异常：{}", e.getMessage());
            return List.of("错误：" + e.getMessage());
        } catch (IOException e) {
            logger.error("查找文件失败，关键词：{}", keyword, e);
            return List.of("错误：查找文件失败 - " + e.getMessage());
        }
    }

    /**
     * 写入/修改文件（仅当 writeEnabled 为 true）。写入成功后自动计算并返回 Unified diff。
     */
    @Tool(description = "写入或修改文件内容。写入成功后自动计算并返回 Unified diff 格式的变更对比。仅在用户明确要求修改代码时使用。")
    public String writeFile(
            @ToolParam(description = "文件相对路径，如 src/main/java/App.java") String relativePath,
            @ToolParam(description = "要写入的完整文件内容") String content
    ) {
        if (!properties.isWriteEnabled()) {
            return "错误：写入功能已被管理员禁用。请在配置文件中设置 agent.code-assistant.write-enabled=true";
        }
        String permError = checkPermission("writeFile", relativePath);
        if (permError != null) {
            return permError;
        }
        try {
            if (content == null) {
                return "错误：内容不能为空";
            }
            if (content.getBytes(StandardCharsets.UTF_8).length > properties.getMaxFileSizeBytes()) {
                return "错误：写入内容超过大小上限 " + properties.getMaxFileSizeBytes() + " 字节";
            }
            Path path = resolvePath(relativePath);
            String diff = computeDiff(path, content, relativePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            logger.info("写入文件成功：{}", relativePath);
            if (diff.isEmpty()) {
                return "文件写入成功：" + relativePath + "（无变更）";
            }
            return "文件写入成功：" + relativePath + "\n\n```diff\n" + diff + "\n```";
        } catch (SecurityException | IllegalArgumentException e) {
            logger.warn("写入文件安全异常：{}", e.getMessage());
            return "错误：" + e.getMessage();
        } catch (IOException e) {
            logger.error("写入文件失败：{}", relativePath, e);
            return "错误：写入文件失败 - " + e.getMessage();
        }
    }

    /**
     * 执行受信任的构建/测试命令（仅当 writeEnabled 为 true）。
     */
    @Tool(description = """
            在项目根目录执行受信任的命令。
            支持 Maven：mvn compile, mvn test, mvn clean
            支持 Git：git status, git diff, git checkout, git log, git stash, git add, git reset, git rev-parse, git branch, git pull --ff-only
            返回结构为 JSON，便于程序解析。
            """)
    public String executeCommand(
            @ToolParam(description = "允许的 Maven/Git 命令") String command
    ) {
        if (!properties.isWriteEnabled()) {
            return errorResponse("命令执行已被禁用");
        }

        if (command == null || command.isBlank()) {
            return errorResponse("命令不能为空");
        }

        // 权限校验
        String permError = checkPermission("executeCommand", command);
        if (permError != null) {
            return errorResponse(permError);
        }

        // 白名单校验（前缀匹配）
        boolean allowed = false;
        for (String prefix : ALLOWED_COMMAND_PREFIXES) {
            if (command.startsWith(prefix)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            return errorResponse("不支持的命令：" + command);
        }

        try {
            Path workDir = getWorkspacePath();
            if (!Files.isDirectory(workDir)) {
                return errorResponse("工作目录不存在：" + workDir);
            }

            // 构建命令（Windows 需用 cmd.exe /c 包装以正确解析 PATH 中的 .cmd/.bat）
            List<String> cmdList = new ArrayList<>();
            if (isWindows()) {
                cmdList.add("cmd.exe");
                cmdList.add("/c");
            }
            cmdList.addAll(Arrays.asList(command.split("\\s+")));
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true); // 合并 stderr 到 stdout

            Process process = pb.start();

            // 等待超时
            boolean finished = process.waitFor(properties.getCommandTimeout(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return errorResponse("命令执行超时（" + properties.getCommandTimeout() + "秒）");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();
            boolean success = exitCode == 0;

            // 构建 JSON 响应
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("exitCode", exitCode);
            result.put("output", output);
            return OBJECT_MAPPER.writeValueAsString(result);

        } catch (SecurityException | IllegalArgumentException e) {
            logger.warn("执行命令安全异常：{}", e.getMessage());
            return errorResponse(e.getMessage());
        } catch (IOException | InterruptedException e) {
            logger.error("执行命令失败：{}", command, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
            return errorResponse("命令执行失败 - " + e.getMessage());
        }
    }

    /**
     * 删除文件或目录。注意：务必确认路径正确，区分根目录 skills/ 和 src/main/resources/skills/ 等相似路径。
     */
    @Tool(description = "删除指定的文件或目录。路径相对于工作空间根目录。目录将递归删除所有子文件。返回被删除的文件列表。执行前务必向用户展示完整路径和将删除的内容。")
    public String deleteFile(
            @ToolParam(description = "要删除的文件或目录相对路径，如 src/main/resources/skills") String relativePath
    ) {
        if (!properties.isWriteEnabled()) {
            return "错误：删除功能已被管理员禁用";
        }
        String permError = checkPermission("deleteFile", relativePath);
        if (permError != null) {
            return permError;
        }
        try {
            if (relativePath == null || relativePath.isBlank()) {
                return "错误：路径不能为空";
            }
            Path path = resolvePath(relativePath);
            if (!Files.exists(path)) {
                return "错误：路径不存在：" + relativePath;
            }

            // 收集将被删除的文件列表，用于展示给用户
            List<String> deletedFiles = new ArrayList<>();
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        deletedFiles.add(relativePath + "/" + path.relativize(p).toString().replace(File.separatorChar, '/'));
                        p.toFile().delete();
                    });
                }
            } else {
                Files.deleteIfExists(path);
                deletedFiles.add(relativePath);
            }

            boolean stillExists = Files.exists(path);
            if (stillExists) {
                return "错误：删除失败，路径仍存在：" + relativePath;
            }
            logger.info("删除成功：{} ({} 项)", relativePath, deletedFiles.size());

            // 限制返回的文件列表长度，避免消息过长
            StringBuilder sb = new StringBuilder();
            sb.append("删除成功：").append(relativePath);
            if (deletedFiles.size() <= 20) {
                sb.append("\n已删除：").append(String.join(", ", deletedFiles));
            } else {
                sb.append("\n已删除 ").append(deletedFiles.size()).append(" 个文件（前 20 个）：")
                        .append(String.join(", ", deletedFiles.subList(0, 20)));
            }
            return sb.toString();
        } catch (SecurityException | IllegalArgumentException e) {
            logger.warn("删除安全异常：{}", e.getMessage());
            return "错误：" + e.getMessage();
        } catch (IOException e) {
            logger.error("删除失败：{}", relativePath, e);
            return "错误：删除失败 - " + e.getMessage();
        }
    }

    @Tool(description = "预先确认一个 ask 级别的操作，调用后该操作在当前会话中将被允许执行。先调用此工具获得用户同意，再执行具体操作。")
    public String confirmOperation(
            @ToolParam(description = "工具名称，如 deleteFile") String toolName,
            @ToolParam(description = "操作的参数（可选），如 src/main/java/Test.java") String arg
    ) {
        String normalizedArg = (arg == null) ? "" : arg;
        permissionEvaluator.approve(toolName, normalizedArg);
        logger.info("操作已确认：{} ({})", toolName, normalizedArg);
        return "操作已确认：" + toolName + " (" + normalizedArg + ")，现在可以安全执行了。";
    }

    @Tool(description = "创建当前工作区快照（git stash），保存所有未提交的修改。在修改代码前调用，以便后续可撤销。")
    public String createSnapshot(
            @ToolParam(description = "快照描述，如 'before fixing bug'") String description
    ) {
        try {
            String desc = (description == null || description.isBlank()) ? "snapshot" : description;
            return runGitCommand("git stash push -m \"snapshot: " + desc + "\"");
        } catch (Exception e) {
            logger.error("创建快照失败", e);
            return "错误：创建快照失败 - " + e.getMessage();
        }
    }

    @Tool(description = "撤销指定文件的修改，恢复到上次提交的状态（git checkout）。配合 createSnapshot 使用。")
    public String undoChanges(
            @ToolParam(description = "文件相对路径，如 src/main/java/App.java。传 . 表示撤销所有更改") String relativePath
    ) {
        try {
            String path = (relativePath == null || relativePath.isBlank()) ? "." : relativePath;
            return runGitCommand("git checkout -- " + path);
        } catch (Exception e) {
            logger.error("撤销更改失败", e);
            return "错误：撤销更改失败 - " + e.getMessage();
        }
    }

    private String runGitCommand(String fullCommand) throws IOException, InterruptedException {
        Path workDir = getWorkspacePath();
        List<String> cmdList = new ArrayList<>(List.of("git"));
        String[] parts = fullCommand.split(" ");
        cmdList.addAll(Arrays.asList(parts).subList(1, parts.length));
        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "命令执行超时";
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            return "命令失败（退出码 " + exitCode + "）：" + output;
        }
        return output.trim();
    }
}