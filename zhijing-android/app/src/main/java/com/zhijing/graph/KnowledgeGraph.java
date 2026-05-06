package com.zhijing.graph;

import android.content.Context;

import com.zhijing.ai.TextExtractor;
import com.zhijing.graph.dao.KnowledgeEntityDao;
import com.zhijing.graph.dao.KnowledgeRelationDao;
import com.zhijing.graph.entity.KnowledgeEntity;
import com.zhijing.graph.entity.KnowledgeRelation;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KnowledgeGraph {

    private final KnowledgeEntityDao entityDao;
    private final KnowledgeRelationDao relationDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public KnowledgeGraph(Context context) {
        ZhijingDatabase db = ZhijingDatabase.getInstance(context);
        this.entityDao = db.entityDao();
        this.relationDao = db.relationDao();
    }

    public void addContent(TextExtractor.ExtractionResult result, Runnable onComplete) {
        executor.execute(() -> {
            List<Long> entityIds = new java.util.ArrayList<>();

            // 写入实体
            for (int i = 0; i < result.entities.size(); i++) {
                String name = result.entities.get(i);
                String type = i < result.entityTypes.size() ? result.entityTypes.get(i) : "CONCEPT";

                KnowledgeEntity existing = entityDao.findByName(name);
                long entityId;
                if (existing != null) {
                    entityId = existing.id;
                    entityDao.updateWeight(entityId, 0.1f, System.currentTimeMillis());
                } else {
                    KnowledgeEntity newEntity = new KnowledgeEntity(name, type);
                    entityId = entityDao.insert(newEntity);
                }
                entityIds.add(entityId);
            }

            // 写入实体间关联（同一内容中的实体两两关联）
            for (int i = 0; i < entityIds.size(); i++) {
                for (int j = i + 1; j < entityIds.size(); j++) {
                    long from = entityIds.get(i);
                    long to = entityIds.get(j);

                    KnowledgeRelation existing = relationDao.findRelation(from, to);
                    if (existing != null) {
                        relationDao.strengthenRelation(from, to, 0.05f);
                    } else {
                        relationDao.insert(new KnowledgeRelation(from, to, "RELATED", 0.5f));
                    }
                }
            }

            if (onComplete != null) onComplete.run();
        });
    }

    public void findRelated(List<Long> entityIds, GraphQueryCallback callback) {
        executor.execute(() -> {
            List<KnowledgeRelation> relations = new java.util.ArrayList<>();
            for (long entityId : entityIds) {
                relations.addAll(relationDao.getAllRelations(entityId));
            }
            callback.onResult(relations);
        });
    }

    public interface GraphQueryCallback {
        void onResult(List<KnowledgeRelation> relations);
    }
}
