package ai.metaranke2e;

import com.snowplowanalytics.snowplow.tracker.Tracker;
import com.snowplowanalytics.snowplow.tracker.emitter.BatchEmitter;
import com.snowplowanalytics.snowplow.tracker.events.Unstructured;
import com.snowplowanalytics.snowplow.tracker.payload.SelfDescribingJson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SnowplowJavaTracker {
    public static void main(String[] args) {
        track();
    }

    public static void track() {
        BatchEmitter emitter = BatchEmitter.builder()
                .url("http://localhost:8082")
                .build();

        Tracker tracker = new Tracker
                .TrackerBuilder(emitter, "trackerNamespace", "appId")
                .build();

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "item");
        payload.put("id", "81f46c34-a4bb-469c-8708-f8127cd67d27");
        payload.put("timestamp", String.valueOf(System.currentTimeMillis()));
        payload.put("item", "item1");

        Map<String, Object> fields = new HashMap<>();
        fields.put("title", "your cat");
        fields.put("color", List.of("white", "black"));
        payload.put("fields", fields);

        Unstructured unstructured = Unstructured.builder()
                .eventData(new SelfDescribingJson("iglu:ai.metarank/item/jsonschema/1-0-0", payload))
                .build();

        tracker.track(unstructured);
        emitter.flushBuffer();
        emitter.close();
    }
}
