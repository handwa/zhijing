package com.zhijing.recommend;

import android.content.Context;

import com.zhijing.graph.KnowledgeGraph;
import com.zhijing.graph.ZhijingDatabase;
import com.zhijing.graph.dao.KnowledgeEntityDao;
import com.zhijing.graph.entity.KnowledgeEntity;
import com.zhijing.graph.entity.KnowledgeRelation;

import java.util.ArrayList;
import java.util.List;

public class RecommendEngine {

    public static class Recommendation {
        public String title;
        public String reason;
        public List<String> explainChain = new ArrayList<>(); // 推导链
        public float score;
    }

    private final KnowledgeGraph graph;
    private final KnowledgeEntityDao entityDao;

    public RecommendEngine(Context context) {
        this.graph = new KnowledgeGraph(context);
        this.entityDao = ZhijingDatabase.getInstance(context).entityDao();
    }

    public void recommend(List<Long> currentEntityIds, RecommendCallback callback) {
        graph.findRelated(currentEntityIds, relations -> {
            List<Recommendation> results = new ArrayList<>();

            for (KnowledgeRelation relation : relations) {
                long targetId = currentEntityIds.contains(relation.fromEntityId)
                        ? relation.toEntityId
                        : relation.fromEntityId;

                if (currentEntityIds.contains(targetId)) continue;

                KnowledgeEntity target = findEntityById(targetId);
                if (target == null) continue;

                Recommendation rec = new Recommendation();
                rec.title = target.name;
                rec.score = relation.strength * target.weight;

                // 构建推导链
                for (long srcId : currentEntityIds) {
                    KnowledgeEntity src = findEntityById(srcId);
                    if (src != null) {
                        rec.explainChain.add(src.name + " → " + relation.relationType + " → " + target.name);
                        rec.reason = "你刚看的内容与\"" + target.name + "\"有关联";
                        break;
                    }
                }

                results.add(rec);
            }

            // 按分数降序排列
            results.sort((a, b) -> Float.compare(b.score, a.score));
            callback.onRecommend(results.size() > 5 ? results.subList(0, 5) : results);
        });
    }

    private KnowledgeEntity findEntityById(long id) {
        List<KnowledgeEntity> all = entityDao.getAll();
        for (KnowledgeEntity e : all) {
            if (e.id == id) return e;
        }
        return null;
    }

    public interface RecommendCallback {
        void onRecommend(List<Recommendation> recommendations);
    }
}
