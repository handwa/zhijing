package com.zhijing.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.zhijing.ai.ContextAnalyzer;
import com.zhijing.ai.TextExtractor;
import com.zhijing.graph.KnowledgeGraph;
import com.zhijing.graph.ZhijingDatabase;
import com.zhijing.graph.entity.UserContent;
import com.zhijing.recommend.RecommendEngine;
import com.zhijing.ui.card.RecommendCardActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShareReceiverActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String sharedText = null;

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            sharedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
        }

        if (sharedText != null && !sharedText.isEmpty()) {
            processSharedText(sharedText);
        } else {
            finish();
        }
    }

    private void processSharedText(String text) {
        String sourceApp = getCallingPackage() != null ? getCallingPackage() : "unknown";

        executor.execute(() -> {
            // 提取关键词和实体
            TextExtractor extractor = new TextExtractor();
            TextExtractor.ExtractionResult result = extractor.extract(text);

            // 保存到本地数据库
            UserContent content = new UserContent(text, sourceApp);
            content.keywords = new com.google.gson.Gson().toJson(result.keywords);
            content.entities = new com.google.gson.Gson().toJson(result.entities);
            ZhijingDatabase.getInstance(this).contentDao().insert(content);

            // 更新知识图谱
            KnowledgeGraph graph = new KnowledgeGraph(this);
            graph.addContent(result, () -> {
                // 查找推荐
                findAndShowRecommendations(result);
            });
        });
    }

    private void findAndShowRecommendations(TextExtractor.ExtractionResult result) {
        if (result.entities.isEmpty()) {
            finish();
            return;
        }

        // 查找实体ID
        List<Long> entityIds = new ArrayList<>();
        for (String entityName : result.entities) {
            com.zhijing.graph.entity.KnowledgeEntity entity =
                    ZhijingDatabase.getInstance(this).entityDao().findByName(entityName);
            if (entity != null) entityIds.add(entity.id);
        }

        if (entityIds.isEmpty()) {
            finish();
            return;
        }

        RecommendEngine engine = new RecommendEngine(this);
        engine.recommend(entityIds, recommendations -> {
            if (!recommendations.isEmpty()) {
                Intent cardIntent = new Intent(this, RecommendCardActivity.class);
                cardIntent.putExtra("title", recommendations.get(0).title);
                cardIntent.putExtra("reason", recommendations.get(0).reason);
                ArrayList<String> chain = new ArrayList<>(recommendations.get(0).explainChain);
                cardIntent.putStringArrayListExtra("chain", chain);
                startActivity(cardIntent);
            }
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
