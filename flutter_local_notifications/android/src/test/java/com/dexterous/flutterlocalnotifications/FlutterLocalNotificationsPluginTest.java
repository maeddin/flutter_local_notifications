package com.dexterous.flutterlocalnotifications;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding.OnSaveInstanceStateListener;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.StandardMethodCodec;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FlutterLocalNotificationsPluginTest {
  private static final String METHOD_CHANNEL = "dexterous.com/flutter/local_notifications";
  private static final String LAUNCHED_APP = "notificationLaunchedApp";
  private static final int NOTIFICATION_ID = 7;
  private static final String PAYLOAD = "payload";

  private BinaryMessenger messenger;
  private FlutterLocalNotificationsPlugin plugin;

  @Before
  public void before() {
    messenger = mock(BinaryMessenger.class);
    plugin = new FlutterLocalNotificationsPlugin();
    plugin.onAttachedToEngine(
        new FlutterPluginBinding(
            ApplicationProvider.getApplicationContext(), null, messenger, null, null, null, null));
  }

  @Test
  public void newActivityAfterTimedOutLaunchDetails_deliversNotificationResponse() {
    Result launchDetails = requestLaunchDetails();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
    assertEquals(false, answeredLaunchDetails(launchDetails).get(LAUNCHED_APP));

    attachActivity(notificationIntent(), null);

    MethodCall call = sentMethodCall();
    assertEquals("didReceiveNotificationResponse", call.method);
    Map<String, Object> response = call.arguments();
    assertEquals(NOTIFICATION_ID, response.get(FlutterLocalNotificationsPlugin.NOTIFICATION_ID));
    assertEquals(PAYLOAD, response.get(FlutterLocalNotificationsPlugin.PAYLOAD));
  }

  @Test
  public void restoredActivityAfterTimedOutLaunchDetails_isNotDelivered() {
    requestLaunchDetails();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

    attachActivity(notificationIntent(), new Bundle());

    verify(messenger, never()).send(anyString(), any(), any());
  }

  @Test
  public void activityFromHistoryAfterTimedOutLaunchDetails_isNotDelivered() {
    requestLaunchDetails();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

    attachActivity(notificationIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY), null);

    verify(messenger, never()).send(anyString(), any(), any());
  }

  @Test
  public void activityWhileLaunchDetailsPending_answersLaunchDetailsOnly() {
    Result launchDetails = requestLaunchDetails();

    attachActivity(notificationIntent(), null);

    assertEquals(true, answeredLaunchDetails(launchDetails).get(LAUNCHED_APP));
    verify(messenger, never()).send(anyString(), any(), any());
  }

  @Test
  public void activityBeforeLaunchDetails_leavesIntentToLaunchDetails() {
    attachActivity(notificationIntent(), null);

    verify(messenger, never()).send(anyString(), any(), any());
    assertEquals(true, answeredLaunchDetails(requestLaunchDetails()).get(LAUNCHED_APP));
  }

  private static Intent notificationIntent() {
    return new Intent("SELECT_NOTIFICATION")
        .putExtra(FlutterLocalNotificationsPlugin.NOTIFICATION_ID, NOTIFICATION_ID)
        .putExtra(FlutterLocalNotificationsPlugin.PAYLOAD, PAYLOAD);
  }

  private Result requestLaunchDetails() {
    Result result = mock(Result.class);
    plugin.onMethodCall(new MethodCall("getNotificationAppLaunchDetails", null), result);
    return result;
  }

  /** Attaches like the embedding does: plugins first, then the restored state of the activity. */
  private void attachActivity(Intent intent, Bundle savedState) {
    Activity activity = mock(Activity.class);
    when(activity.getIntent()).thenReturn(intent);
    ActivityPluginBinding binding = mock(ActivityPluginBinding.class);
    when(binding.getActivity()).thenReturn(activity);
    List<OnSaveInstanceStateListener> stateListeners = new ArrayList<>();
    doAnswer(invocation -> stateListeners.add(invocation.getArgument(0)))
        .when(binding)
        .addOnSaveStateListener(any());

    plugin.onAttachedToActivity(binding);
    for (OnSaveInstanceStateListener listener : stateListeners) {
      listener.onRestoreInstanceState(savedState);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> answeredLaunchDetails(Result result) {
    ArgumentCaptor<Object> details = ArgumentCaptor.forClass(Object.class);
    verify(result).success(details.capture());
    return (Map<String, Object>) details.getValue();
  }

  private MethodCall sentMethodCall() {
    ArgumentCaptor<ByteBuffer> message = ArgumentCaptor.forClass(ByteBuffer.class);
    verify(messenger).send(eq(METHOD_CHANNEL), message.capture(), any());
    ByteBuffer buffer = message.getValue();
    buffer.rewind();
    return StandardMethodCodec.INSTANCE.decodeMethodCall(buffer);
  }
}
