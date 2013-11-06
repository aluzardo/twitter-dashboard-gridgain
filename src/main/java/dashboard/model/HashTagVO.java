package dashboard.model;

import com.google.common.base.Objects;
import org.gridgain.grid.cache.query.GridCacheQuerySqlField;
import org.springframework.social.twitter.api.HashTagEntity;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;


public class HashTagVO implements Externalizable {

    @GridCacheQuerySqlField(unique = true, index = true)
    private String GUID;

    @GridCacheQuerySqlField(index = true)
    private String text;

    @GridCacheQuerySqlField(index = true)
    private String tweetGUID;

    public HashTagVO() {
    }

    HashTagVO(String tweetGUID, HashTagEntity hashHasTagEntity) {
        this.tweetGUID = tweetGUID;
        this.text = hashHasTagEntity.getText();
        this.GUID = UUID.randomUUID().toString();
    }

    public String getGUID() {
        return GUID;
    }

    public String getText() {
        return text;
    }

    public String getTweetGUID() {
        return tweetGUID;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(GUID, text, tweetGUID);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final HashTagVO other = (HashTagVO) obj;
        return Objects.equal(this.GUID, other.GUID) && Objects.equal(this.text, other.text) && Objects.equal(this.tweetGUID, other.tweetGUID);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(tweetGUID);
        out.writeObject(text);
        out.writeObject(GUID);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        tweetGUID = (String)in.readObject();
        text = (String)in.readObject();
        GUID = (String)in.readObject();
    }
}
