package dashboard.web.service;

import com.google.common.collect.Lists;
import dashboard.core.model.HashTagVO;
import dashboard.core.model.KeyValuePair;
import dashboard.core.model.TweetVO;
import dashboard.core.twitter.TweetStreamListener;
import dashboard.core.utils.GridUtils;
import org.apache.commons.lang.StringUtils;
import org.gridgain.grid.Grid;
import org.gridgain.grid.GridException;
import org.gridgain.grid.cache.GridCache;
import org.gridgain.grid.cache.query.GridCacheQuery;
import org.gridgain.grid.lang.GridClosure;
import org.gridgain.grid.lang.GridReducer;
import org.gridgain.grid.streamer.GridStreamer;
import org.gridgain.grid.streamer.GridStreamerContext;
import org.gridgain.grid.streamer.GridStreamerWindow;
import org.gridgain.grid.streamer.index.GridStreamerIndex;
import org.gridgain.grid.streamer.index.GridStreamerIndexEntry;
import org.gridgain.grid.util.lang.GridFunc;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.social.twitter.api.Stream;
import org.springframework.social.twitter.api.StreamListener;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.*;

@Service
public class TwitterServiceImpl implements TwitterService {

    public static final int MAX_NUM_RETURNED = 5;
    public static final int MULTIPLIER = 55;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private Twitter twitter;


    @Override
    @Async
    public void ingest(int duration) {

        Stream sampleStream = null;

        final Grid grid = GridUtils.getGrid();

        final GridStreamer tweetStreamer = grid.streamer(GridUtils.STREAMER_NAME);

        try {
            List<StreamListener> listeners = Lists.newArrayList();
            listeners.add(new TweetStreamListener(tweetStreamer, MULTIPLIER));

            sampleStream = twitter.streamingOperations().sample(listeners);

            Thread.sleep(duration);

        } catch (InterruptedException e) {
            log.error("stream thread interrupted...", e);
        } finally {
            log.debug("closing stream");

            if (tweetStreamer != null) {
                tweetStreamer.reset();
                tweetStreamer.resetMetrics();

            }

            if (sampleStream != null) {
                sampleStream.close();
            }
        }
    }

    @Override
    public List<KeyValuePair> getHashTagSummary(final String windowName) {

        final Grid grid = GridUtils.getGrid();

        final GridStreamer streamer = grid.streamer(GridUtils.STREAMER_NAME);

        final GridClosure<GridStreamerContext, Collection<GridStreamerIndexEntry<HashTagVO, String, Long>>> gridClosure = new GridClosure<GridStreamerContext, Collection<GridStreamerIndexEntry<HashTagVO, String, Long>>>() {

            @Override
            public Collection<GridStreamerIndexEntry<HashTagVO, String, Long>> apply(GridStreamerContext gridStreamerContext) {

                final GridStreamerWindow<HashTagVO> gridStreamerWindow = gridStreamerContext.window(windowName);

                final GridStreamerIndex<HashTagVO, String, Long> index = gridStreamerWindow.index();

                return index.entries(0);

            }
        };

        final GridReducer<Collection<GridStreamerIndexEntry<HashTagVO, String, Long>>, Collection<GridStreamerIndexEntry<HashTagVO, String, Long>>> gridReducer = new GridReducer<Collection<GridStreamerIndexEntry<HashTagVO, String, Long>>, Collection<GridStreamerIndexEntry<HashTagVO, String, Long>>>() {
            private List<GridStreamerIndexEntry<HashTagVO, String, Long>> sorted = new ArrayList<>();


            @Override
            public boolean collect(@Nullable Collection<GridStreamerIndexEntry<HashTagVO, String, Long>> gridStreamerIndexEntries) {
                if (gridStreamerIndexEntries != null && !gridStreamerIndexEntries.isEmpty()) {
                    sorted.addAll(gridStreamerIndexEntries);
                }

                return true;
            }

            @Override
            public Collection<GridStreamerIndexEntry<HashTagVO, String, Long>> reduce() {
                Collections.sort(sorted, new Comparator<GridStreamerIndexEntry<HashTagVO, String, Long>>() {

                    @Override
                    public int compare(GridStreamerIndexEntry<HashTagVO, String, Long> o1, GridStreamerIndexEntry<HashTagVO, String, Long> o2) {
                        return o2.value().compareTo(o1.value());
                    }
                });

                return GridFunc.retain(sorted, true, MAX_NUM_RETURNED);
            }
        };


        List<KeyValuePair> results = Lists.newArrayList();

        try {

            Collection<GridStreamerIndexEntry<HashTagVO, String, Long>> reduceResults = streamer.context().reduce(gridClosure, gridReducer);

            for (GridStreamerIndexEntry<HashTagVO, String, Long> entry : reduceResults) {
                results.add(new KeyValuePair(StringUtils.abbreviate(entry.key(), 20), NumberFormat.getNumberInstance().format(entry.value())));
            }

        } catch (GridException e) {
            log.error("grid exception occurred...", e);
        }

        return results;

    }


    @Override
    public List<KeyValuePair> getTopTweeters() {
        final Grid grid = GridUtils.getGrid();

        final GridStreamer streamer = grid.streamer(GridUtils.STREAMER_NAME);

        final GridClosure<GridStreamerContext, Collection<GridStreamerIndexEntry<TweetVO, String, Long>>> gridClosure = new GridClosure<GridStreamerContext, Collection<GridStreamerIndexEntry<TweetVO, String, Long>>>() {

            @Override
            public Collection<GridStreamerIndexEntry<TweetVO, String, Long>> apply(GridStreamerContext gridStreamerContext) {

                final GridStreamerWindow<TweetVO> gridStreamerWindow = gridStreamerContext.window(GridUtils.TOP_TWEETERS_WINDOW);

                final GridStreamerIndex<TweetVO, String, Long> index = gridStreamerWindow.index();

                return index.entries(0);

            }
        };

        final GridReducer<Collection<GridStreamerIndexEntry<TweetVO, String, Long>>, Collection<GridStreamerIndexEntry<TweetVO, String, Long>>> gridReducer = new GridReducer<Collection<GridStreamerIndexEntry<TweetVO, String, Long>>, Collection<GridStreamerIndexEntry<TweetVO, String, Long>>>() {
            private List<GridStreamerIndexEntry<TweetVO, String, Long>> sorted = new ArrayList<>();


            @Override
            public boolean collect(@Nullable Collection<GridStreamerIndexEntry<TweetVO, String, Long>> gridStreamerIndexEntries) {
                if (gridStreamerIndexEntries != null && !gridStreamerIndexEntries.isEmpty()) {
                    sorted.addAll(gridStreamerIndexEntries);
                }

                return true;
            }

            @Override
            public Collection<GridStreamerIndexEntry<TweetVO, String, Long>> reduce() {
                Collections.sort(sorted, new Comparator<GridStreamerIndexEntry<TweetVO, String, Long>>() {

                    @Override
                    public int compare(GridStreamerIndexEntry<TweetVO, String, Long> o1, GridStreamerIndexEntry<TweetVO, String, Long> o2) {
                        return o2.value().compareTo(o1.value());
                    }
                });

                return GridFunc.retain(sorted, true, MAX_NUM_RETURNED);
            }
        };

        List<KeyValuePair> results = Lists.newArrayList();

        try {

            Collection<GridStreamerIndexEntry<TweetVO, String, Long>> reduceResults = streamer.context().reduce(gridClosure, gridReducer);

            for (GridStreamerIndexEntry<TweetVO, String, Long> entry : reduceResults) {
                results.add(new KeyValuePair(StringUtils.abbreviate(entry.key(), 20), NumberFormat.getNumberInstance().format(entry.value())));
            }

        } catch (GridException e) {
            log.error("grid exception occurred...", e);
        }

        return results;

    }

    @Override
    public long getTotalTweets() {

        final Grid grid = GridUtils.getGrid();

        final GridStreamer streamer = grid.streamer(GridUtils.STREAMER_NAME);

        final Object o = streamer.context().localSpace().get(GridUtils.TOTAL_TWEETS);

        if (o != null) {
            return (Long) o;
        }

        return 0L;

    }

    @Override
    public long getTotalTweetsWithHashTag() {
        final Grid grid = GridUtils.getGrid();

        final GridStreamer streamer = grid.streamer(GridUtils.STREAMER_NAME);

        final Object o = streamer.context().localSpace().get(GridUtils.TOTAL_TWEETS_NO_HASH_TAGS);

        if (o != null) {
            return (Long) o;
        }

        return 0L;
    }

    @Override
    public List<TweetVO> findTweets(String text, String screenName) {

        final Grid grid = GridUtils.getGrid();

        final GridCache<String, TweetVO> cache = grid.cache(TweetVO.class.getName());

        List<TweetVO> tweets = Lists.newArrayList();

        try {

            List<String> parameters = Lists.newArrayList();
            StringBuilder s = new StringBuilder("fake = 'false'");

            if (StringUtils.isNotBlank(text)) {
                s.append(" and text like ?");
                parameters.add("%" + text + "%");
            }
            if (StringUtils.isNotBlank(screenName)) {
                s.append(" and screenName = ?");
                parameters.add(screenName);
            }

            s.append(" limit 100");

            final String sql = s.toString();

            if (log.isDebugEnabled()) {
                log.debug("findTweets sql [" + sql + "]");
            }

            GridCacheQuery<Map.Entry<String, TweetVO>> query = cache.queries().createSqlQuery(TweetVO.class, sql);

            final Collection<Map.Entry<String, TweetVO>> searchResults = query.execute(parameters.toArray()).get();

            for (Map.Entry<String, TweetVO> entry : searchResults) {
                tweets.add(entry.getValue());
            }

        } catch (GridException e) {
            log.error("error getting tweets with text [" + text + "]", e);
        }


        return tweets;
    }

}
