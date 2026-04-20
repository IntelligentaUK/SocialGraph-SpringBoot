package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intelligenta.socialgraph.persistence.TimelineStore;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Infinispan embedded-cache implementation of {@link TimelineStore}. Stores a
 * LinkedHashMap of (postId → score) per user per timeline kind; reads iterate
 * the map, sort by score DESC, and slice. Phase I-H promotes the ranked reads
 * to an Ickle {@code ORDER BY} query against an indexed cache of timeline
 * entries for cluster-scale workloads.
 */
@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanTimelineStore implements TimelineStore {

    private final EmbeddedCacheManager manager;

    public InfinispanTimelineStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private Cache<String, ArrayList<String>> fifo() {
        return (Cache<String, ArrayList<String>>) (Cache<?, ?>) manager.getCache("timelines-fifo");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, LinkedHashMap<String, Double>> personal() {
        return (Cache<String, LinkedHashMap<String, Double>>)
            (Cache<?, ?>) manager.getCache("timelines-personal");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, LinkedHashMap<String, Double>> everyone() {
        return (Cache<String, LinkedHashMap<String, Double>>)
            (Cache<?, ?>) manager.getCache("timelines-everyone");
    }

    @Override
    public void push(String uid, String postId, double fifoTimestamp,
                     double personalScore, double everyoneScore) {
        ArrayList<String> fifoList = fifo().getOrDefault(uid, new ArrayList<>());
        ArrayList<String> nextFifo = new ArrayList<>(fifoList);
        nextFifo.add(0, postId);
        fifo().put(uid, nextFifo);

        LinkedHashMap<String, Double> p = personal().getOrDefault(uid, new LinkedHashMap<>());
        LinkedHashMap<String, Double> pNext = new LinkedHashMap<>(p);
        pNext.put(postId, personalScore);
        personal().put(uid, pNext);

        LinkedHashMap<String, Double> e = everyone().getOrDefault(uid, new LinkedHashMap<>());
        LinkedHashMap<String, Double> eNext = new LinkedHashMap<>(e);
        eNext.put(postId, everyoneScore);
        everyone().put(uid, eNext);
    }

    @Override
    public void pushMany(List<String> recipientUids, String postId,
                         double fifoTimestamp, double personalScore, double everyoneScore) {
        for (String uid : recipientUids) {
            push(uid, postId, fifoTimestamp, personalScore, everyoneScore);
        }
    }

    @Override
    public List<String> range(String uid, Kind kind, int offset, int limit) {
        return switch (kind) {
            case FIFO -> {
                ArrayList<String> list = fifo().get(uid);
                if (list == null || list.isEmpty()) yield Collections.emptyList();
                int from = Math.max(0, offset);
                int to = Math.min(list.size(), offset + limit);
                yield from >= list.size() ? Collections.emptyList() : new ArrayList<>(list.subList(from, to));
            }
            case PERSONAL_IMPORTANCE -> sortedSlice(personal().get(uid), offset, limit);
            case EVERYONE_IMPORTANCE -> sortedSlice(everyone().get(uid), offset, limit);
        };
    }

    private List<String> sortedSlice(Map<String, Double> scores, int offset, int limit) {
        if (scores == null || scores.isEmpty()) return Collections.emptyList();
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Double>, Double>comparing(Map.Entry::getValue).reversed());
        int from = Math.max(0, offset);
        int to = Math.min(sorted.size(), offset + limit);
        if (from >= sorted.size()) return Collections.emptyList();
        List<String> out = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) out.add(sorted.get(i).getKey());
        return out;
    }
}
