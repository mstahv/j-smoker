package in.virit.meater.cloud;

import java.util.List;

public interface MeaterCloudListener {

    default void onDevicesUpdated(List<MeaterDevice> devices) {}

    default void onError(Exception error) {}
}
