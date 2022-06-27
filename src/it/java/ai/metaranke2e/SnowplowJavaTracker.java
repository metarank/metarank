package ai.metaranke2e;

import com.snowplowanalytics.snowplow.tracker.Tracker;
import com.snowplowanalytics.snowplow.tracker.emitter.BatchEmitter;
import com.snowplowanalytics.snowplow.tracker.events.Unstructured;
import com.snowplowanalytics.snowplow.tracker.payload.SelfDescribingJson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SnowplowJavaTracker {
    public static void main(String[] args) {
        track("http://localhost:8082");
    }

    public static void track(String host) {
        BatchEmitter emitter = BatchEmitter.builder()
                .url(host)
                .build();

        Tracker tracker = new Tracker
                .TrackerBuilder(emitter, "trackerNamespace", "appId")
                .build();

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "item");
        payload.put("id", "81f46c34-a4bb-469c-8708-f8127cd67d27");
        payload.put("timestamp", String.valueOf(System.currentTimeMillis()));
        payload.put("item", "item1");

        List<Object> fields = new ArrayList<>();
        Map<String, Object> title = new HashMap<>();
        title.put("name", "title");
        title.put("value", "your cat");
        fields.add(title);
        payload.put("fields", fields);

        Unstructured unstructured = Unstructured.builder()
                .eventData(new SelfDescribingJson("iglu:ai.metarank/item/jsonschema/1-0-0", payload))
                .build();

        tracker.track(unstructured);
        emitter.flushBuffer();
        emitter.close();
    }
}
