package dashboard.web.service;

import com.google.common.collect.Lists;
import dashboard.core.hpc.HashTagClosure;
import dashboard.core.hpc.HashTagReducer;
import dashboard.core.hpc.TweetClosure;
import dashboard.core.hpc.TweetReducer;
import dashboard.core.model.HashTagVO;
import dashboard.core.model.TweetVO;
import dashboard.core.twitter.TweetStreamListener;
import dashboard.core.utils.GridConstants;
import dashboard.core.utils.GridUtils;
import dashboard.web.model.KeyValuePair;
import org.apache.commons.lang.StringUtils;
import org.gridgain.grid.Grid;
import org.gridgain.grid.GridException;
import org.gridgain.grid.cache.GridCache;
import org.gridgain.grid.cache.query.GridCacheQuery;
import org.gridgain.grid.streamer.GridStreamer;
import org.gridgain.grid.streamer.index.GridStreamerIndexEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.social.twitter.api.Stream;
import org.springframework.social.twitter.api.StreamListener;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class TwitterServiceImpl implements TwitterService {


    public static final int MULTIPLIER = 55;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private Twitter twitter;


    @Override
    @Async
    public void ingest(int duration) {

        Stream sampleStream = null;

        final Grid grid = GridUtils.getGrid();

        final GridStreamer tweetStreamer = grid.streamer(GridConstants.STREAMER_NAME);

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
    public List<KeyValuePair> getHashTagSummary(String windowName) {

        final Grid grid = GridUtils.getGrid();

        final GridStreamer streamer = grid.streamer(GridConstants.STREAMER_NAME);

        List<KeyValuePair> results = Lists.newArrayList();

        try {

            Collection<GridStreamerIndexEntry<HashTagVO, String, Long>> reduceResults = streamer.context().reduce(new HashTagClosure(windowName), new HashTagReducer());

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

        final GridStreamer streamer = grid.streamer(GridConstants.STREAMER_NAME);

        List<KeyValuePair> results = Lists.newArrayList();

        try {

            Collection<GridStreamerIndexEntry<TweetVO, String, Long>> reduceResults = streamer.context().reduce(new TweetClosure(), new TweetReducer());

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

        final GridStreamer streamer = grid.streamer(GridConstants.STREAMER_NAME);

        final Object o = streamer.context().localSpace().get(GridConstants.TOTAL_TWEETS);

        if (o != null) {
            return (Long) o;
        }

        return 0L;

    }

    @Override
    public long getTotalTweetsWithHashTag() {
        final Grid grid = GridUtils.getGrid();

        final GridStreamer streamer = grid.streamer(GridConstants.STREAMER_NAME);

        final Object o = streamer.context().localSpace().get(GridConstants.TOTAL_TWEETS_NO_HASH_TAGS);

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
