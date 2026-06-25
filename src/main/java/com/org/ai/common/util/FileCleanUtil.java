package com.org.ai.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * 文件清理工具类
 * <p>
 * 提供递归删除目录/文件的能力，解决 AI 工具无删除权限的问题。
 * 直接运行 main 方法即可执行预设的清理任务。
 * </p>
 */
public final class FileCleanUtil {

    private static final Logger log = LoggerFactory.getLogger(FileCleanUtil.class);

    private FileCleanUtil() {
        // 工具类禁止实例化
    }

    /**
     * 递归删除指定路径的文件或目录
     *
     * @param targetPath 要删除的路径（文件或目录）
     * @return true 删除成功，false 删除失败
     */
    public static boolean delete(String targetPath) {
        Path path = Paths.get(targetPath).normalize();
        if (!Files.exists(path)) {
            log.warn("路径不存在，跳过删除: {}", path);
            return false;
        }

        try {
            // 防止目录穿越攻击：校验路径在项目范围内
            String canonicalPath = path.toFile().getCanonicalPath();
            String projectRoot = new File("").getCanonicalPath();
            if (!canonicalPath.startsWith(projectRoot)) {
                throw new SecurityException("禁止删除项目目录之外的文件: " + canonicalPath);
            }

            if (Files.isDirectory(path)) {
                // 递归删除目录
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (file.delete()) {
                                log.info("已删除: {}", file.getAbsolutePath());
                            } else {
                                log.warn("删除失败: {}", file.getAbsolutePath());
                            }
                        });
            } else {
                Files.deleteIfExists(path);
                log.info("已删除文件: {}", path);
            }

            // 最终验证
            boolean exists = Files.exists(path);
            if (exists) {
                log.error("删除验证失败，路径仍然存在: {}", path);
                return false;
            }
            log.info("删除成功: {}", path);
            return true;
        } catch (SecurityException e) {
            log.error("安全校验未通过: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            log.error("删除过程中发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 快速入口：删除 src/main/resources/skills/ 目录
     */
    public static void main(String[] args) {
        String targetDir = "src/main/resources/skills";
        log.info("开始清理任务: {}", targetDir);
        boolean result = delete(targetDir);
        if (result) {
            log.info("清理任务完成 ✅");
        } else {
            log.error("清理任务失败 ❌");
        }
    }
}
