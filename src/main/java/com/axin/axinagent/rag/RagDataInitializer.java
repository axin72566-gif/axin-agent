package com.axin.axinagent.rag;

import com.axin.axinagent.config.RagProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 数据初始化器
 * 应用启动时将 resources/document/ 目录下所有 .md 文件按标题切分，
 * 每个 Markdown 标题段落（一个 Q&A）作为一个独立 chunk 写入 Redis Stack。
 * <p>
 * 可通过配置 axin.rag.init-on-startup=false 跳过初始化（已有数据时推荐关闭）。
 */
@Component
@Slf4j
@ConditionalOnBean(VectorStore.class)
public class RagDataInitializer implements CommandLineRunner {

    @Resource
    private RagProperties ragProperties;

    @Resource
    private VectorStore vectorStore;

    @Override
    public void run(String... args) throws Exception {
        if (!ragProperties.isInitOnStartup()) {
            log.info("RAG 初始化已通过配置关闭");
            return;
        }
        // 幂等检查：若向量库已有数据则跳过初始化，避免重复写入
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder().query("恋爱").topK(1).build()
        );
        if (existing != null && !existing.isEmpty()) {
            log.info("========== RAG 向量库已有数据，跳过初始化 ==========");
            return;
        }

        log.info("========== 开始初始化 RAG 数据（Redis 向量存储）==========");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:document/*.md");

        if (resources.length == 0) {
            log.warn("未找到任何 .md 文档，跳过初始化");
            return;
        }

        List<Document> allDocuments = new ArrayList<>();

        for (org.springframework.core.io.Resource resource : resources) {
            String filename = resource.getFilename();
            log.info("正在加载文档：{}", filename);

            // withHorizontalRuleCreateDocument(false)：不按 --- 分割
            // MarkdownDocumentReader 默认按标题（#/##/###/####）将文档切成多个 Document，
            // 每个标题段落独立成一个 chunk，天然对应一个完整 Q&A
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(false)
                    .withIncludeCodeBlock(true)
                    .withIncludeBlockquote(true)
                    // 附加文件来源元数据，检索结果可溯源
                    .withAdditionalMetadata("source", "love-knowledge-base")
                    .withAdditionalMetadata("filename", filename)
                    .build();

            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            List<Document> docs = reader.read();

            // 将标题写入每个 chunk 的 metadata，便于后续按标题过滤检索
            docs.forEach(doc -> {
                Object title = doc.getMetadata().get("title");
                if (title != null) {
                    doc.getMetadata().put("question", title.toString());
                }
            });

            allDocuments.addAll(docs);
            log.info("  -> 按标题切分出 {} 个 chunk", docs.size());
        }

        log.info("全部文档解析完成，共 {} 个 chunk，开始向量化写入 Redis...", allDocuments.size());

        // 每个 chunk 就是一个完整 Q&A，无需再二次分块，直接写入
        vectorStore.add(allDocuments);

        log.info("========== RAG 数据初始化完成，共写入 {} 个向量 ==========", allDocuments.size());
    }
}
