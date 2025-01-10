package com.calsignlabs.apde;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.CompilerProblem;
import com.calsignlabs.apde.build.ComponentTarget;
import com.calsignlabs.apde.build.CopyAndroidJarTask;
import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.build.SketchPreviewerBuilder;
import com.calsignlabs.apde.build.dag.BuildContext;
import com.calsignlabs.apde.build.dag.ModularBuild;
import com.calsignlabs.apde.dialogs.WhatsNewDialog;
import com.calsignlabs.apde.support.ResizeAnimation;
import com.calsignlabs.apde.tool.FindReplace;
import com.calsignlabs.apde.tool.Tool;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.ParserConfigurationException;
import processing.data.XML;
import static com.calsignlabs.apde.R.id.*;

/** This is the editor, or the main activity of APDE */
public class EditorActivity extends AppCompatActivity {

  private HashMap<String, KeyBinding> keyBindings;

  private DrawerLayout drawer;

  public Toolbar toolbar;

  public ViewPager codePager;
  public FragmentStatePagerAdapter codePagerAdapter;

  protected TabLayout codeTabStrip;
  protected ViewGroup tabBarContainer;

  protected ImageButton undoButton;
  protected ImageButton redoButton;

  protected ArrayList<SketchFile> tabs;

  private boolean saved;

  private ActionBarDrawerToggle drawerToggle;
  private boolean drawerOpen;

  private APDE.SketchLocation drawerSketchLocationType;
  private String drawerSketchPath;
  private boolean drawerRecentSketch;

  protected boolean keyboardVisible;
  private boolean firstResize = true;
  private int oldCodeHeight = -1;
  private boolean consoleWasHidden = false;

  private ViewPager consoleWrapperPager;

  public ArrayList<CompilerProblem> compilerProblems;
  private ListView problemOverviewList;
  private ProblemOverviewListAdapter problemOverviewListAdapter;

  private View extraHeaderView;

  private final static int RENAME_TAB = 0;
  private final static int NEW_TAB = 1;

  private MessageTouchListener messageListener;

  private int message = -1;

  private ConsoleStream outStream;
  private ConsoleStream errStream;
  protected AtomicBoolean FLAG_SUSPEND_OUT_STREAM = new AtomicBoolean(false);

  private BroadcastReceiver consoleBroadcastReceiver;
  private MessageClient.OnMessageReceivedListener wearConsoleReceiver;

  private boolean building;

  /** Type of message displayed in the message bar - message, error, or warning. */
  protected enum MessageType {
    MESSAGE,
    ERROR,
    WARNING;

    public String serialize() {
      return toString();
    }

    public static MessageType deserialize(String serialized) {
      switch (serialized) {
        case "MESSAGE":
          return MESSAGE;
        case "ERROR":
          return ERROR;
        case "WARNING":
          return WARNING;

        case "true":
          return ERROR;
        case "false":
          return MESSAGE;
        default:
          return MESSAGE;
      }
    }
  }

  private MessageType messageType = MessageType.MESSAGE;

  private boolean charInserts = false;
  private boolean problemOverview = false;
  private ImageButton toggleCharInserts;
  private ImageButton toggleProblemOverview;

  public static int FLAG_DELETE_APK = 5;

  public static int FLAG_LAUNCH_SKETCH = 6;

  public static int FLAG_SET_WALLPAPER = 7;

  public static int FLAG_RUN_PREVIEW = 8;

  public ScheduledThreadPoolExecutor autoSaveTimer;
  public ScheduledFuture<?> autoSaveTimerTask;
  public Runnable autoSaveTimerAction =
      () -> {
        runOnUiThread(this::autoSave);
      };

  public ScheduledThreadPoolExecutor autoCompileTimer;
  public ScheduledFuture<?> autoCompileTask;
  public Runnable autoCompileAction = this::autoCompile;

  protected ComponentTarget componentTarget;

  private boolean FLAG_SCREEN_OVERLAY_INSTALL_ANYWAY = false;
  private boolean FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED = false;
  private boolean FLAG_FIRST_AUTO_COMPILE = true;

  @SuppressLint("NewApi")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_editor);

    toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
    setSupportActionBar(toolbar);

    outStream = new ConsoleStream(System.out);
    errStream = new ConsoleStream(System.err);

    System.setOut(new PrintStream(outStream));
    System.setErr(new PrintStream(errStream));

    consoleBroadcastReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            char severity = intent.getCharExtra("com.calsignlabs.apde.LogSeverity", 'o');
            String message = intent.getStringExtra("com.calsignlabs.apde.LogMessage");
            String exception = intent.getStringExtra("com.calsignlabs.apde.LogException");

            handleSketchConsoleLog(severity, message, exception);
          }
        };

    registerReceiver(
        consoleBroadcastReceiver, new IntentFilter("com.calsignlabs.apde.LogBroadcast"));

    wearConsoleReceiver =
        messageEvent -> {
          if (messageEvent.getPath().equals("/apde_receive_logs")) {
            try {
              JSONObject json = new JSONObject(new String(messageEvent.getData()));
              String severityStr = json.getString("severity");

              if (severityStr.length() != 1) {
                System.err.println(
                    "Wear console receiver - invalid severity: \"" + severityStr + "\"");
                return;
              }

              char severity = severityStr.charAt(0);
              String message = json.getString("message");
              String exception = json.getString("exception");

              handleSketchConsoleLog(severity, message, exception);
            } catch (JSONException e) {
              e.printStackTrace();
            }
          }
        };

    Wearable.getMessageClient(this).addListener(wearConsoleReceiver);

    getGlobalState().initTaskManager();

    getGlobalState().getSketchbookDrive();

    tabs = new ArrayList<SketchFile>();

    codePager = findViewById(R.id.code_pager);
    codePagerAdapter =
        new FragmentStatePagerAdapter(getSupportFragmentManager()) {
          @Override
          public int getCount() {
            return tabs.size();
          }

          @Override
          public CharSequence getPageTitle(int position) {
            return tabs.get(position).getTitle();
          }

          @Override
          public Fragment getItem(int position) {
            return tabs.get(position).getFragment();
          }

          @Override
          public int getItemPosition(Object object) {
            return POSITION_NONE;
          }
        };
    codePager.setAdapter(codePagerAdapter);

    codeTabStrip = findViewById(R.id.code_pager_tabs);
    codeTabStrip.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
    codeTabStrip.setSelectedTabIndicatorColor(getResources().getColor(R.color.holo_select));
    codeTabStrip.setSelectedTabIndicatorHeight(
        (int)
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics()));
    codeTabStrip.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {

            if (getSelectedCodeArea() != null) {
              getSelectedCodeArea().updateCursorCompilerProblem();
            }

            correctUndoRedoEnabled();
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            View anchor = ((LinearLayout) codeTabStrip.getChildAt(0)).getChildAt(tab.getPosition());
            EditorActivity.this.onTabReselected(anchor);
          }
        });

    tabBarContainer = findViewById(R.id.tab_bar_container);

    undoButton = findViewById(R.id.undo_redo_undo);
    redoButton = findViewById(R.id.undo_redo_redo);

    getGlobalState().assignLongPressDescription(undoButton, R.string.editor_menu_undo);
    getGlobalState().assignLongPressDescription(redoButton, R.string.editor_menu_redo);

    undoButton.setOnClickListener(view -> undo());
    redoButton.setOnClickListener(view -> redo());

    Manifest.loadPermissions(this);

    messageListener = new MessageTouchListener();

    findViewById(R.id.buffer).setOnLongClickListener(messageListener);
    findViewById(R.id.buffer).setOnTouchListener(messageListener);

    setSaved(false);

    getGlobalState().rebuildToolList();

    getGlobalState().setEditor(this);

    getSupportActionBar().setTitle(getGlobalState().getSketchName());

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    int oldVersionNum = prefs.getInt("version_num", -1);
    int realVersionNum = getGlobalState().appVersionCode();

    boolean justUpdated = realVersionNum > oldVersionNum;

    if (justUpdated) {

      runUpgradeChanges(oldVersionNum, realVersionNum);

      SharedPreferences.Editor edit = prefs.edit();
      edit.putInt("version_num", realVersionNum);
      edit.apply();
    }

    drawer = findViewById(R.id.drawer);
    ListView drawerList = findViewById(R.id.drawer_list);

    drawerSketchLocationType = null;
    drawerSketchPath = "";

    forceDrawerReload();

    drawerToggle =
        new ActionBarDrawerToggle(
            this,
            drawer,
            toolbar,
            R.string.drawer_open_accessibility_text,
            R.string.drawer_close_accessibility_text) {
          @Override
          public void onDrawerClosed(View view) {
            if (isSelectedCodeAreaInitialized()) {
              getSelectedCodeArea().setEnabled(true);
            }
            supportInvalidateOptionsMenu();
          }

          @Override
          public void onDrawerSlide(View drawer, float slide) {
            super.onDrawerSlide(drawer, slide);

            if (slide > 0) {
              if (isSelectedCodeAreaInitialized()) {
                getSelectedCodeArea().setEnabled(false);
              }
              supportInvalidateOptionsMenu();
              drawerOpen = true;

              if (drawerSketchLocationType != null) {
                getSupportActionBar()
                    .setSubtitle(
                        drawerSketchLocationType.toReadableString(getGlobalState())
                            + drawerSketchPath
                            + "/");
              } else if (drawerRecentSketch) {
                getSupportActionBar()
                    .setSubtitle(getResources().getString(R.string.drawer_folder_recent) + "/");
              } else {
                getSupportActionBar().setSubtitle(null);
              }
            } else {

              if (isSelectedCodeAreaInitialized()) {
                getSelectedCodeArea().setEnabled(true);
              }
              supportInvalidateOptionsMenu();
              drawerOpen = false;

              getSupportActionBar().setSubtitle(null);
            }
          }

          @Override
          public void onDrawerOpened(View drawerView) {
            if (isSelectedCodeAreaInitialized()) {
              getSelectedCodeArea().setEnabled(false);
            }
            supportInvalidateOptionsMenu();
          }
        };
    drawer.addDrawerListener(drawerToggle);

    drawerList.setOnItemClickListener(
        (parent, view, position, id) -> {
          FileNavigatorAdapter.FileItem item =
              ((FileNavigatorAdapter) drawerList.getAdapter()).getItem(position);

          if (drawerSketchLocationType == null && !drawerRecentSketch) {
            switch (position) {
              case 0:
                drawerSketchLocationType = APDE.SketchLocation.SKETCHBOOK;
                break;
              case 1:
                drawerSketchLocationType = APDE.SketchLocation.EXAMPLE;
                break;
              case 2:
                drawerSketchLocationType = APDE.SketchLocation.LIBRARY_EXAMPLE;
                break;
              case 3:
                drawerSketchLocationType = APDE.SketchLocation.TEMPORARY;
                break;
              case 4:
                drawerSketchLocationType = null;
                drawerSketchPath = "";
                drawerRecentSketch = true;
                break;
            }
          } else {
            switch (item.getType()) {
              case NAVIGATE_UP:
                int lastSlash = drawerSketchPath.lastIndexOf('/');
                if (lastSlash > 0) {
                  drawerSketchPath = drawerSketchPath.substring(0, lastSlash);
                } else if (drawerSketchPath.length() > 0) {
                  drawerSketchPath = "";
                } else {
                  drawerSketchLocationType = null;
                }

                if (drawerRecentSketch) {
                  drawerRecentSketch = false;
                }

                break;
              case MESSAGE:
                break;
              case FOLDER:
                drawerSketchPath += "/" + item.getText();

                break;
              case SKETCH:
                autoSave();

                if (drawerRecentSketch) {
                  APDE.SketchMeta sketch = getGlobalState().getRecentSketches().get(position - 1);

                  loadSketch(sketch.getPath(), sketch.getLocation());
                } else {
                  loadSketch(drawerSketchPath + "/" + item.getText(), drawerSketchLocationType);
                }

                drawer.closeDrawers();

                break;
            }
          }

          forceDrawerReload();
        });

    drawerList.setOnDragListener(
        new View.OnDragListener() {
          float THRESHOLD =
              TypedValue.applyDimension(
                  TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());

          @Override
          public boolean onDrag(View view, DragEvent event) {

            switch (event.getAction()) {
              case DragEvent.ACTION_DRAG_LOCATION:
                float y = event.getY();
                float h = drawerList.getHeight();

                float upDif = y - THRESHOLD;
                float downDif = y - (h - THRESHOLD);

                if (upDif < 0) {
                  drawerList.smoothScrollBy((int) upDif, 300);
                }
                if (downDif > 0) {
                  drawerList.smoothScrollBy((int) upDif, 300);
                }

                break;
            }

            return true;
          }
        });

    getSupportActionBar().setHomeButtonEnabled(true);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    View activityRootView = findViewById(R.id.content);

    activityRootView
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new OnGlobalLayoutListener() {
              private View buffer = findViewById(R.id.buffer);
              private TextView messageArea = findViewById(R.id.message);
              private View console = findViewById(R.id.console_wrapper);
              private View content = findViewById(R.id.content);
              private FrameLayout autoCompileProgress =
                  findViewById(R.id.auto_compile_progress_wrapper);

              private int previousVisibleHeight = -1;

              @SuppressWarnings("deprecation")
              @Override
              public void onGlobalLayout() {

                Rect r = new Rect();
                activityRootView.getWindowVisibleDisplayFrame(r);
                int visibleHeight = r.bottom - r.top;
                if (visibleHeight == previousVisibleHeight) {
                  return;
                }
                previousVisibleHeight = visibleHeight;

                int heightDiff = activityRootView.getRootView().getHeight() - visibleHeight;

                if (oldCodeHeight == -1) {
                  oldCodeHeight = codePager.getHeight();
                }

                if (message == -1) {
                  message = buffer.getHeight();
                }

                int totalHeight =
                    content.getHeight()
                        - message
                        - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)
                        - tabBarContainer.getHeight()
                        - autoCompileProgress.getHeight();

                boolean keyboardCoveringScreen =
                    heightDiff
                        > getResources().getDimension(R.dimen.keyboard_visibility_change_threshold);
                boolean allowSoftKeyboard =
                    !PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                        .getBoolean("use_hardware_keyboard", false);

                if (keyboardCoveringScreen && !keyboardVisible) {

                  if (!allowSoftKeyboard) {
                    InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(activityRootView.getWindowToken(), 0);
                    return;
                  }

                  keyboardVisible = true;

                  if (firstResize) {
                    firstResize = false;
                  } else {
                    oldCodeHeight = codePager.getHeight();
                  }

                  if (totalHeight > oldCodeHeight) {
                    codePager.startAnimation(
                        new ResizeAnimation<LinearLayout>(
                            codePager,
                            ResizeAnimation.DEFAULT,
                            ResizeAnimation.DEFAULT,
                            ResizeAnimation.DEFAULT,
                            totalHeight));
                    console.startAnimation(
                        new ResizeAnimation<LinearLayout>(
                            console,
                            ResizeAnimation.DEFAULT,
                            ResizeAnimation.DEFAULT,
                            ResizeAnimation.DEFAULT,
                            0));
                  } else {
                    codePager.setLayoutParams(
                        new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, totalHeight));
                    console.setLayoutParams(
                        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
                  }

                  switch (messageType) {
                    case MESSAGE:
                      buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back));
                      buffer.setBackgroundColor(getResources().getColor(R.color.message_back));
                      messageArea.setTextColor(getResources().getColor(R.color.message_text));
                      break;
                    case ERROR:
                      buffer.setBackgroundDrawable(
                          getResources().getDrawable(R.drawable.back_error));
                      buffer.setBackgroundColor(getResources().getColor(R.color.error_back));
                      messageArea.setTextColor(getResources().getColor(R.color.error_text));
                      break;
                    case WARNING:
                      buffer.setBackgroundDrawable(
                          getResources().getDrawable(R.drawable.back_warning));
                      buffer.setBackgroundColor(getResources().getColor(R.color.warning_back));
                      messageArea.setTextColor(getResources().getColor(R.color.warning_text));
                      break;
                  }
                  CodeEditText codeArea = getSelectedCodeArea();

                  if (codeArea != null) {
                    codeArea.updateBracketMatch();
                  }

                  if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                      .getBoolean("char_inserts", true)) {

                    toggleCharInsertsProblemOverviewButton(false, true);

                    if (charInserts) {
                      View charInsertTray = findViewById(R.id.char_insert_tray);

                      correctMessageAreaHeight();
                      toggleCharInserts.setImageResource(
                          messageType != MessageType.MESSAGE
                              ? R.drawable.ic_caret_right_white
                              : R.drawable.ic_caret_right_black);

                      messageArea.getLayoutParams().width = 0;
                      charInsertTray.setVisibility(View.VISIBLE);
                      charInsertTray.getLayoutParams().width =
                          findViewById(R.id.message_char_insert_wrapper).getWidth();

                      messageArea.requestLayout();
                      charInsertTray.requestLayout();
                    }
                  }
                } else if (!keyboardCoveringScreen && keyboardVisible) {

                  codePager.startAnimation(
                      new ResizeAnimation<LinearLayout>(
                          codePager,
                          ResizeAnimation.DEFAULT,
                          ResizeAnimation.DEFAULT,
                          ResizeAnimation.DEFAULT,
                          oldCodeHeight,
                          false));
                  console.startAnimation(
                      new ResizeAnimation<LinearLayout>(
                          console,
                          ResizeAnimation.DEFAULT,
                          totalHeight - codePager.getHeight(),
                          ResizeAnimation.DEFAULT,
                          totalHeight - oldCodeHeight,
                          false));

                  keyboardVisible = false;

                  if (oldCodeHeight > 0) {
                    consoleWasHidden = false;
                  }

                  if (getSelectedCodeArea() != null) {

                    getSelectedCodeArea().clearFocus();
                    getSelectedCodeArea().matchingBracket = -1;
                  }

                  toggleCharInsertsProblemOverviewButton(true, true);

                  findViewById(R.id.message).setVisibility(View.VISIBLE);
                  findViewById(R.id.char_insert_tray).setVisibility(View.GONE);

                  hideCharInsertsNoAnimation(false);
                } else if (keyboardVisible) {

                  codePager.getLayoutParams().height = totalHeight;
                  console.getLayoutParams().height = 0;

                  codePager.requestLayout();
                  console.requestLayout();
                } else {

                  codePager.getLayoutParams().height = Math.min(totalHeight, codePager.getHeight());
                  console.getLayoutParams().height =
                      Math.max(totalHeight - codePager.getHeight(), 0);

                  codePager.requestLayout();
                  console.requestLayout();
                }
              }
            });

    toggleCharInserts = findViewById(R.id.toggle_char_inserts);
    toggleCharInserts.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            toggleCharInserts();
          }
        });
    toggleProblemOverview = findViewById(R.id.toggle_problem_overview);
    toggleProblemOverview.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            toggleProblemOverview();
          }
        });

    keyBindings = new HashMap<String, KeyBinding>();

    try {

      loadKeyBindings(new XML(getResources().getAssets().open("default_key_bindings.xml")));
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    }

    if (justUpdated
        && PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pref_whats_new_enable", true)) {
      WhatsNewDialog.show(this);
    } else if (savedInstanceState == null) {

      getGlobalState().initExamplesRepo();
    }

    getGlobalState().disableSsl3();

    codePagerAdapter.notifyDataSetChanged();
    codeTabStrip.setupWithViewPager(codePager);

    compilerProblems = new ArrayList<>();
    problemOverviewList = findViewById(R.id.problem_overview_list);
    problemOverviewListAdapter =
        new ProblemOverviewListAdapter(this, R.layout.problem_overview_list_item, compilerProblems);

    getConsoleWrapper().setClickable(false);

    problemOverviewList.setAdapter(problemOverviewListAdapter);

    problemOverviewList.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            CompilerProblem problem = problemOverviewListAdapter.getItem(i);
            highlightTextExt(
                problem.sketchFile.getIndex(), problem.line, problem.start, problem.length);
            if (problem.isError()) {
              errorExt(problem.getMessage());
            } else {
              warningExt(problem.getMessage());
            }
          }
        });

    problemOverviewList.setOnItemLongClickListener(
        (adapterView, view, i, l) -> {
          ClipboardManager clipboardManager =
              (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
          if (clipboardManager != null) {
            CompilerProblem problem = problemOverviewListAdapter.getItem(i);
            String text = getProblemOverviewDescription(EditorActivity.this, problem).toString();
            ClipData clipData =
                ClipData.newPlainText(
                    getResources().getString(R.string.problem_overview_list_copy_description),
                    text);
            clipboardManager.setPrimaryClip(clipData);

            Toast.makeText(
                    EditorActivity.this,
                    R.string.problem_overview_list_copy_toast_message,
                    Toast.LENGTH_SHORT)
                .show();

            return true;
          } else {
            return false;
          }
        });

    consoleWrapperPager = findViewById(R.id.console_wrapper_pager);
    consoleWrapperPager.setAdapter(
        new PagerAdapter() {
          @Override
          public int getCount() {
            return 2;
          }

          @Override
          public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
          }

          @Override
          public Object instantiateItem(ViewGroup container, int position) {
            switch (position) {
              case 0:
                return findViewById(R.id.console_scroller);
              case 1:
                return findViewById(R.id.problem_overview_wrapper);
              default:
                return new Object();
            }
          }

          @Override
          public void destroyItem(ViewGroup container, int position, Object object) {}
        });

    if (false) {
      autoSaveTimer =
          new IdlingScheduledThreadPoolExecutor(
              "autoSaveTimer", 1, Executors.defaultThreadFactory());
      autoCompileTimer =
          new IdlingScheduledThreadPoolExecutor(
              "autoCompileTimer", 1, Executors.defaultThreadFactory());
    } else {
      autoSaveTimer = new ScheduledThreadPoolExecutor(1);
      autoCompileTimer = new ScheduledThreadPoolExecutor(1);
    }

    setComponentTarget(ComponentTarget.PREVIEW);

    try {

      if (!loadSketchStart()) {
        getGlobalState().selectNewTempSketch();
        addDefaultTab(APDE.DEFAULT_SKETCH_TAB);
        autoSave();
      }

      getGlobalState().writeCodeDeletionDebugStatus("onCreate() after loadSketchStart()");
    } catch (Exception e) {

      e.printStackTrace();
    }

    if (FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED) {
      setComponentTarget(ComponentTarget.PREVIEW);
      FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED = false;
    }

    toggleAutoCompileIndicator(false);
  }

  @Override
  public void onStart() {
    super.onStart();

    getGlobalState().writeCodeDeletionDebugStatus("onStart()");

    APDE.StorageDrive.StorageDriveType storageDriveType =
        getGlobalState().getSketchbookStorageDrive().type;

    if (storageDriveType.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL)
        || storageDriveType.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)) {

      if (ContextCompat.checkSelfPermission(
              this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {

        ActivityCompat.requestPermissions(
            this,
            new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
            PERMISSIONS_REQUEST_CODE);
      }
    }
  }

  protected void handleSketchConsoleLog(char severity, String message, String exception) {

    switch (severity) {
      case 'o':
        postConsole(message);
        break;
      case 'e':
        postConsole(message);
        break;
      case 'x':
        errorExt(message != null ? exception.concat(": ").concat(message) : exception);
        break;
    }
  }

  protected final int PERMISSIONS_REQUEST_CODE = 42;

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String permissions[], int[] grantResults) {
    switch (requestCode) {
      case PERMISSIONS_REQUEST_CODE:
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {}

        break;
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  public ComponentTarget getComponentTarget() {
    return componentTarget;
  }

  public void setComponentTarget(ComponentTarget componentTarget) {
    this.componentTarget = componentTarget;
    invalidateOptionsMenu();
    if (!FLAG_FIRST_AUTO_COMPILE) {
      scheduleAutoCompile(true);
    }
  }

  public int getSelectedCodeIndex() {
    return codePager.getCurrentItem();
  }

  public void selectCode(int index) {
    codePager.setCurrentItem(index);
  }

  public int getSketchFileIndex(SketchFile sketchFile) {
    return tabs.indexOf(sketchFile);
  }

  public int getCodeCount() {
    return tabs.size();
  }

  public boolean isSelectedCodeAreaInitialized() {
    return getSelectedSketchFile() != null
        && getSelectedSketchFile().getFragment().getCodeEditText() != null;
  }

  public CodeEditText getSelectedCodeArea() {
    return getSelectedSketchFile() != null
        ? getSelectedSketchFile().getFragment().getCodeEditText()
        : null;
  }

  public ScrollView getSelectedCodeAreaScroller() {
    return getSelectedSketchFile() != null
        ? getSelectedSketchFile().getFragment().getCodeScroller()
        : null;
  }

  public SketchFile getSelectedSketchFile() {
    return tabs.size() > 0 && getSelectedCodeIndex() < tabs.size()
        ? tabs.get(getSelectedCodeIndex())
        : null;
  }

  /**
   * Used internally to manage the "What's New" screen
   *
   * @param adapter
   * @param items
   * @param more
   * @return whether or not more items can be added
   */
  public static boolean addWhatsNewItem(
      ListView list,
      ArrayAdapter<String> adapter,
      Stack<String> items,
      Button more,
      boolean fullScroll) {

    if (items.empty()) {
      more.setVisibility(View.GONE);
      return false;
    }

    adapter.add(items.pop());

    if (fullScroll) {

      list.smoothScrollToPosition(adapter.getCount());
    }

    if (items.empty()) {
      more.setVisibility(View.GONE);
      return false;
    } else {
      more.setVisibility(View.VISIBLE);
      return true;
    }
  }

  public static Stack<String> getReleaseNotesStack(Context context) {
    String fullText = APDE.readAssetFile(context, "whatsnew.txt");

    List<String> releaseNotes =
        Arrays.asList(
            fullText.split(
                "(\\r\\n|\\n|\\r)------------------------------------------------------------------------(\\r\\n|\\n|\\r)"));

    for (int i = 0; i < releaseNotes.size(); i++) {
      releaseNotes.set(i, releaseNotes.get(i).trim());
    }

    Collections.reverse(releaseNotes);

    Stack<String> releaseNotesStack = new Stack<String>();
    releaseNotesStack.addAll(releaseNotes);

    return releaseNotesStack;
  }

  public TabLayout getCodeTabStrip() {
    return codeTabStrip;
  }

  public ViewPager getCodePager() {
    return codePager;
  }

  public View getConsoleWrapper() {
    return findViewById(R.id.console_wrapper);
  }

  public void setExtraHeaderView(View headerView) {
    extraHeaderView = headerView;
  }

  public View getExtraHeaderView() {
    return extraHeaderView;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    getGlobalState().writeCodeDeletionDebugStatus("onSaveInstanceState()");

    try {
      TextView messageArea = (TextView) findViewById(R.id.message);
      TextView console = (TextView) findViewById(R.id.console);
      ScrollView consoleScroller = (ScrollView) findViewById(R.id.console_scroller);
      HorizontalScrollView consoleScrollerX =
          (HorizontalScrollView) findViewById(R.id.console_scroller_x);

      outState.putString("consoleText", console.getText().toString());
      outState.putInt("consoleScrollPos", consoleScroller.getScrollY());
      outState.putInt("consoleScrollPosX", consoleScrollerX.getScrollX());
      outState.putString("messageText", messageArea.getText().toString());
      outState.putString("messageIsError", messageType.serialize());
    } catch (Exception e) {

      e.printStackTrace();
    }

    super.onSaveInstanceState(outState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    try {

      List<Fragment> fragments = getSupportFragmentManager().getFragments();
      if (fragments != null) {
        for (Fragment fragment : fragments) {
          if (fragment != null) {
            getSupportFragmentManager().beginTransaction().remove(fragment).commit();
          }
        }
      }

      if (savedInstanceState != null) {
        String consoleText = savedInstanceState.getString("consoleText");
        int consoleScrollPos = savedInstanceState.getInt("consoleScrollPos");
        int consoleScrollPosX = savedInstanceState.getInt("consoleScrollPosX");
        String messageText = savedInstanceState.getString("messageText");
        MessageType msgType =
            MessageType.deserialize(savedInstanceState.getString("messageIsError"));

        TextView console = (TextView) findViewById(R.id.console);
        ScrollView consoleScroller = (ScrollView) findViewById(R.id.console_scroller);
        HorizontalScrollView consoleScrollerX =
            (HorizontalScrollView) findViewById(R.id.console_scroller_x);

        if (consoleText != null) {

          console.setText(consoleText);

          switch (msgType) {
            case MESSAGE:
              message(messageText);
              break;
            case ERROR:
              error(messageText);
              break;
            case WARNING:
              warning(messageText);
              break;
          }

          console.post(
              new Runnable() {
                @Override
                public void run() {
                  consoleScroller.scrollTo(0, consoleScrollPos);
                  consoleScrollerX.scrollTo(consoleScrollPosX, 0);
                }
              });
        }
      }
    } catch (Exception e) {

      e.printStackTrace();
    }

    getGlobalState().writeCodeDeletionDebugStatus("onRestoreInstanceState()");
  }

  private static SparseArray<ActivityResultCallback> activityResultCodes =
      new SparseArray<ActivityResultCallback>();

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    getGlobalState().writeCodeDeletionDebugStatus("onActivityResult()");

    if (requestCode == FLAG_LAUNCH_SKETCH) {

      if (resultCode == RESULT_OK) {

        if (getGlobalState().getPref("pref_debug_delay_before_run_sketch", false)) {

          Handler handler = new Handler(getMainLooper());
          handler.postDelayed(
              () -> runOnUiThread((() -> Build.launchSketchPostLaunch(this))), 1000);
        } else {
          Build.launchSketchPostLaunch(this);
        }
      }
      Build.cleanUpPostLaunch(this);
    } else if (requestCode == FLAG_SET_WALLPAPER) {

      if (resultCode == RESULT_OK) {

        Build.setWallpaperPostLaunch(this);
      }
      Build.cleanUpPostLaunch(this);
    } else if (requestCode == FLAG_RUN_PREVIEW) {

      if (resultCode == RESULT_OK) {
        if (getComponentTarget() == ComponentTarget.PREVIEW) {
          runApplication();
        }
      }
    }

    ActivityResultCallback action = activityResultCodes.get(requestCode);

    if (action != null) {
      action.onActivityResult(requestCode, resultCode, data);

      activityResultCodes.remove(requestCode);
    }

    super.onActivityResult(requestCode, resultCode, data);
  }

  public void selectFile(Intent intent, int requestCode, ActivityResultCallback callback) {
    activityResultCodes.put(requestCode, callback);
    startActivityForResult(intent, requestCode);
  }

  public interface ActivityResultCallback {
    void onActivityResult(int requestCode, int resultCode, Intent data);
  }

  @SuppressLint("NewApi")
  public void onResume() {
    super.onResume();

    getGlobalState().writeCodeDeletionDebugStatus("onResume()");

    ((TextView) findViewById(R.id.console))
        .setTextSize(
            Integer.parseInt(
                PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("textsize_console", "14")));

    if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        .getBoolean("use_hardware_keyboard", false)) {
      getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    } else {
      getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    initCodeAreaAndConsoleDimensions();
    correctMessageAreaHeight();

    int minWidth;
    int maxWidth;

    Point point = new Point();
    getWindowManager().getDefaultDisplay().getSize(point);
    maxWidth = point.x;

    minWidth =
        maxWidth
            - (int)
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics())
                * 2;

    findViewById(R.id.console).setMinimumWidth(minWidth);

    Intent intent = getIntent();

    if (intent.getAction() != null
        && (intent.getAction().equals(Intent.ACTION_VIEW)
            || intent.getAction().equals(Intent.ACTION_EDIT))
        && intent.getData() != null) {
      String scheme = intent.getData().getScheme();
      String filePath = intent.getData().getPath();

      if (scheme != null
          && (scheme.equalsIgnoreCase("file") || scheme.equalsIgnoreCase("content"))
          && filePath != null
          && filePath.length() != 0) {

        if (!loadExternalSketch(filePath)) {

          String crop = filePath;

          if (crop.length() > 1) {
            while (crop.charAt(0) == '/') crop = crop.substring(1);
          }

          int slashIndex = crop.indexOf('/');
          if (slashIndex != -1 && crop.length() > slashIndex + 1) {
            crop = crop.substring(slashIndex);

            while (crop.length() > 2 && crop.charAt(1) == '/') crop = crop.substring(1);
          }
          if (!loadExternalSketch(crop)) {

            loadExternalSketch(Environment.getExternalStorageDirectory().getPath() + filePath);
          }
        }
      }
    }

    getGlobalState().initProcessingPrefs();

    registerReceiver(
        consoleBroadcastReceiver, new IntentFilter("com.calsignlabs.apde.LogBroadcast"));

    supportInvalidateOptionsMenu();

    correctUndoRedoEnabled();

    scheduleAutoCompile(true);

    FLAG_FIRST_AUTO_COMPILE = false;
  }

  public void correctUndoRedoEnabled() {
    boolean settingsEnabled = getGlobalState().getPref("pref_key_undo_redo", true);

    findViewById(R.id.undo_redo_container)
        .setVisibility(settingsEnabled && !getGlobalState().isExample() ? View.VISIBLE : View.GONE);

    SketchFile sketchFile = getSelectedSketchFile();
    boolean canUndo = sketchFile != null && sketchFile.canUndo();
    boolean canRedo = sketchFile != null && sketchFile.canRedo();

    undoButton.setEnabled(canUndo);
    redoButton.setEnabled(canRedo);
    undoButton.setClickable(canUndo);
    redoButton.setClickable(canRedo);

    int alphaEnabled = getResources().getInteger(R.integer.prop_menu_comp_select_alpha_selected);
    int alphaDisabled = getResources().getInteger(R.integer.prop_menu_comp_select_alpha_unselected);

    undoButton.setImageAlpha(canUndo ? alphaEnabled : alphaDisabled);
    redoButton.setImageAlpha(canRedo ? alphaEnabled : alphaDisabled);
  }

  public void undo() {
    if (getSelectedCodeIndex() >= 0 && getSelectedCodeIndex() < tabs.size()) {
      tabs.get(getSelectedCodeIndex()).undo(this);
      correctUndoRedoEnabled();
    }
  }

  public void redo() {
    if (getSelectedCodeIndex() >= 0 && getSelectedCodeIndex() < tabs.size()) {
      tabs.get(getSelectedCodeIndex()).redo(this);
      correctUndoRedoEnabled();
    }
  }

  private boolean loadExternalSketch(String filePath) {

    File file = new File(filePath);

    String ext = "";
    int lastDot = filePath.lastIndexOf('.');
    if (lastDot != -1) {
      ext = filePath.substring(lastDot);
    }

    if (ext.equalsIgnoreCase(".pde") && file.exists() && !file.isDirectory()) {

      File sketchFolder = file.getParentFile();

      loadSketch(sketchFolder.getAbsolutePath(), APDE.SketchLocation.EXTERNAL);
      message(getGlobalState().getString(R.string.sketch_load_external_success));
      return true;
    }

    return false;
  }

  public HashMap<String, KeyBinding> getKeyBindings() {
    return keyBindings;
  }

  /**
   * Load specified key bindings from the XML resource
   *
   * @param xml
   */
  public void loadKeyBindings(XML xml) {
    XML[] bindings = xml.getChildren();
    for (XML binding : bindings) {

      if (!binding.getName().equals("binding")) continue;

      String name = binding.getContent();
      int key = binding.getInt("key");

      String modifiers = binding.getString("mod");
      String[] mods = modifiers.split("\\|");

      boolean ctrl = false;
      boolean meta = false;
      boolean func = false;

      boolean alt = false;
      boolean sym = false;
      boolean shift = false;

      for (String mod : mods) {
        if (mod.equals("ctrl")) ctrl = true;
        if (mod.equals("meta")) meta = true;
        if (mod.equals("func")) func = true;

        if (mod.equals("alt")) alt = true;
        if (mod.equals("sym")) sym = true;
        if (mod.equals("shift")) shift = true;
      }

      KeyBinding bind = new KeyBinding(name, key, ctrl, meta, func, alt, sym, shift);

      keyBindings.put(name, bind);
    }
  }

  @SuppressLint("NewApi")
  @Override
  public boolean onKeyDown(int key, KeyEvent event) {

    boolean ctrl = event.isCtrlPressed();
    boolean meta = event.isMetaPressed();
    boolean func = event.isFunctionPressed();

    boolean alt = event.isAltPressed();
    boolean sym = event.isSymPressed();
    boolean shift = event.isShiftPressed();

    if (keyBindings.get("save_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
      saveSketch();
      return true;
    }
    if (keyBindings.get("new_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
      createNewSketch();
      return true;
    }
    if (keyBindings.get("open_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
      loadSketch();
      return true;
    }

    if (keyBindings.get("run_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
      runApplication();
      return true;
    }
    if (keyBindings.get("stop_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
      stopApplication();
      return true;
    }

    if (keyBindings.get("new_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
      if (!getGlobalState().isExample()) addTabWithDialog();
      return true;
    }
    if (keyBindings.get("delete_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
      if (!getGlobalState().isExample()) deleteTab();
      return true;
    }
    if (keyBindings.get("rename_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
      if (!getGlobalState().isExample()) renameTab();
      return true;
    }

    if (keyBindings.get("undo").matches(key, ctrl, meta, func, alt, sym, shift)) {
      if (!getGlobalState().isExample()
          && getCodeCount() > 0
          && PreferenceManager.getDefaultSharedPreferences(this)
              .getBoolean("pref_key_undo_redo", true)) {
        undo();
      }
      return true;
    }
    if (keyBindings.get("redo").matches(key, ctrl, meta, func, alt, sym, shift)) {
      if (!getGlobalState().isExample()
          && getCodeCount() > 0
          && PreferenceManager.getDefaultSharedPreferences(this)
              .getBoolean("pref_key_undo_redo", true)) {
        redo();
      }

      return true;
    }

    if (keyBindings.get("view_sketches").matches(key, ctrl, meta, func, alt, sym, shift)) {
      loadSketch();
      selectDrawerFolder(APDE.SketchLocation.SKETCHBOOK);
      return true;
    }
    if (keyBindings.get("view_examples").matches(key, ctrl, meta, func, alt, sym, shift)) {
      loadSketch();
      selectDrawerFolder(APDE.SketchLocation.EXAMPLE);
      return true;
    }
    if (keyBindings.get("view_recent").matches(key, ctrl, meta, func, alt, sym, shift)) {
      loadSketch();
      selectDrawerRecent();
      return true;
    }

    if (keyBindings.get("settings").matches(key, ctrl, meta, func, alt, sym, shift)) {
      launchSettings();
      return true;
    }
    if (keyBindings.get("sketch_properties").matches(key, ctrl, meta, func, alt, sym, shift)) {
      launchSketchProperties();
      return true;
    }
    if (keyBindings.get("sketch_permissions").matches(key, ctrl, meta, func, alt, sym, shift)) {

      return true;
    }

    if (keyBindings.get("show_sketch_folder").matches(key, ctrl, meta, func, alt, sym, shift)) {
      getGlobalState().launchSketchFolder(this);
      return true;
    }
    if (keyBindings.get("add_file").matches(key, ctrl, meta, func, alt, sym, shift)) {

      return true;
    }

    KeyBinding press = new KeyBinding("press", key, ctrl, meta, func, alt, sym, shift);
    boolean toolShortcut = false;

    for (Tool tool : getGlobalState().getTools()) {
      KeyBinding toolBinding = tool.getKeyBinding();

      if (toolBinding != null && toolBinding.matches(press)) {
        tool.run();
        toolShortcut = true;
      }
    }

    if (toolShortcut) {
      return true;
    }

    return super.onKeyDown(key, event);
  }

  /**
   * Saves the current sketch and sets up the editor with a blank sketch, from the context of the
   * editor.
   */
  public void createNewSketch() {

    autoSave();

    getGlobalState().selectNewTempSketch();

    newSketch();

    forceDrawerReload();

    getSupportActionBar().setTitle(getGlobalState().getSketchName());
  }

  /** Open the rename sketch AlertDialog */
  public void launchRenameSketch() {
    MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(this);

    alert.setTitle(
        String.format(
            Locale.US,
            getResources().getString(R.string.rename_sketch_title),
            getGlobalState().getSketchName()));
    alert.setMessage(R.string.rename_sketch_message);

    EditText input =
        getGlobalState()
            .createAlertDialogEditText(this, alert, getGlobalState().getSketchName(), true);

    alert.setPositiveButton(
        R.string.rename_sketch_button,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            String sketchName = input.getText().toString();

            if (validateSketchName(sketchName)
                && !sketchName.equals(getGlobalState().getSketchName())) {
              getGlobalState().setSketchName(sketchName);

              APDE.SketchMeta source =
                  new APDE.SketchMeta(
                      getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
              APDE.SketchMeta dest =
                  new APDE.SketchMeta(source.getLocation(), source.getParent() + "/" + sketchName);

              getGlobalState().moveFolder(source, dest, EditorActivity.this);
            }

            if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("use_hardware_keyboard", false)) {
              ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
          }
        });

    alert.setNegativeButton(
        R.string.cancel,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("use_hardware_keyboard", false)) {
              ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
          }
        });

    AlertDialog dialog = alert.create();
    if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        .getBoolean("use_hardware_keyboard", false)) {
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    dialog.show();
  }

  /** Called when the user selects "Load Sketch" - this will open the navigation drawer */
  private void loadSketch() {

    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer);
    RelativeLayout drawerLayout = (RelativeLayout) findViewById(R.id.drawer_wrapper);

    drawer.openDrawer(drawerLayout);
  }

  public int getSketchCount() {
    File sketchbookLoc = getGlobalState().getSketchbookFolder();
    int count = 0;
    if (sketchbookLoc.exists()) {
      File[] folders = sketchbookLoc.listFiles();
      for (File folder : folders) if (folder.isDirectory()) count++;
    }

    return count;
  }

  protected int drawerIndexOfSketch(String name) {

    File sketchbookLoc = getGlobalState().getSketchbookFolder();

    int index = 1;

    if (sketchbookLoc.exists()) {

      File[] folders = sketchbookLoc.listFiles();

      Arrays.sort(folders);

      for (File folder : folders) {
        if (folder.isDirectory()) {

          if (folder.getName().equals(name)) return index;

          index++;
        }
      }
    }

    return -1;
  }

  protected static boolean copyAssetFolder(
      AssetManager assetManager, String fromAssetPath, String toPath) {
    try {
      String[] files = assetManager.list(fromAssetPath);
      new File(toPath).mkdirs();
      boolean res = true;
      for (String file : files)
        if (file.contains("."))
          res &= copyAsset(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
        else res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);

      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
    InputStream in = null;
    OutputStream out = null;
    try {
      in = assetManager.open(fromAssetPath);
      new File(toPath).createNewFile();
      out = new FileOutputStream(toPath);
      copyFile(in, out);
      in.close();
      in = null;
      out.flush();
      out.close();
      out = null;

      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static void copyFile(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[1024];
    int read;
    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
  }

  @Override
  public void onPause() {

    getGlobalState().writeCodeDeletionDebugStatus("onPause()");

    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

    super.onPause();
  }

  @Override
  public void onStop() {
    getGlobalState().writeCodeDeletionDebugStatus("onStop()");

    saveSketchForStop();

    getGlobalState().writeCodeDeletionDebugStatus("onStop() after saveSketchForStop");

    super.onStop();
  }

  @Override
  public void onDestroy() {

    unregisterReceiver(consoleBroadcastReceiver);

    outStream.disable();
    errStream.disable();

    getGlobalState().writeCodeDeletionDebugStatus("onDestroy()");

    super.onDestroy();
  }

  /** Saves the sketch for when the activity is closing */
  public void saveSketchForStop() {
    getGlobalState().writeCodeDeletionDebugStatus("begin saveSketchForStop()");

    autoSave();

    String sketchPath = getGlobalState().getSketchPath();

    String sketchLocation = getGlobalState().getSketchLocationType().toString();

    StringBuilder sketchData = new StringBuilder();
    sketchData.append(sketchPath);
    sketchData.append(';');
    sketchData.append(sketchLocation);
    sketchData.append(';');
    sketchData.append(getSelectedCodeIndex());
    sketchData.append(';');
    sketchData.append(getComponentTarget().serialize());
    sketchData.append(';');

    JSONObject undoRedoHistories = new JSONObject();

    for (int i = 0; i < tabs.size(); i++) {
      SketchFile sketchFile = tabs.get(i);

      if (sketchFile.getFragment() != null && sketchFile.getFragment().isInitialized()) {
        sketchData.append(sketchFile.getFragment().getCodeEditText().getSelectionStart());
        sketchData.append(',');
        sketchData.append(sketchFile.getFragment().getCodeEditText().getSelectionEnd());
        sketchData.append(',');
        sketchData.append(sketchFile.getFragment().getCodeScroller().getScrollX());
        sketchData.append(',');
        sketchData.append(sketchFile.getFragment().getCodeScroller().getScrollY());
        sketchData.append(';');
      } else {

        sketchData.append(sketchFile.getSelectionStart());
        sketchData.append(',');
        sketchData.append(sketchFile.getSelectionEnd());
        sketchData.append(',');
        sketchData.append(sketchFile.getScrollX());
        sketchData.append(',');
        sketchData.append(sketchFile.getScrollY());
        sketchData.append(';');
      }

      try {
        undoRedoHistories.put(sketchFile.getFilename(), sketchFile.getUndoRedoHistory());
      } catch (JSONException | NoSuchAlgorithmException e) {
        e.printStackTrace();
      }
    }

    writeTempFile("sketchData.txt", sketchData.toString());
    writeTempFile("sketchUndoRedoHistory.json", undoRedoHistories.toString());

    getGlobalState().writeCodeDeletionDebugStatus("end saveSketchForStop()");
  }

  /**
   * Loads the temporary sketch for the start of the app
   *
   * @return success
   */
  public boolean loadSketchStart() {
    try {
      String sketchData = readTempFile("sketchData.txt");
      String[] data = sketchData.split(";");
      String jsonData = readTempFile("sketchUndoRedoHistory.json");

      if (data.length < 4 || jsonData.length() == 0) {

        return false;
      }

      JSONObject undoRedoHistories = new JSONObject(jsonData);

      String sketchPath = data[0];
      APDE.SketchLocation sketchLocation = APDE.SketchLocation.fromString(data[1]);

      boolean success = loadSketch(sketchPath, sketchLocation);

      selectCode(Integer.parseInt(data[2]));

      setComponentTarget(ComponentTarget.deserialize(Integer.parseInt(data[3])));

      if (success && tabs.size() == data.length - 4) {
        for (int i = 4; i < data.length; i++) {
          String[] sketchFileData = data[i].split(",");

          if (sketchFileData.length > 0) {
            tabs.get(i - 4).selectionStart = Integer.parseInt(sketchFileData[0]);
            tabs.get(i - 4).selectionEnd = Integer.parseInt(sketchFileData[1]);
            tabs.get(i - 4).scrollX = Integer.parseInt(sketchFileData[2]);
            tabs.get(i - 4).scrollY = Integer.parseInt(sketchFileData[3]);
          }
        }
      }

      if (success) {
        for (SketchFile sketchFile : tabs) {
          try {
            sketchFile.populateUndoRedoHistory(
                undoRedoHistories.getJSONObject(sketchFile.getFilename()));
          } catch (Exception e) {
            /* If an exception gets through, then this function reports that it was
             * not successful. The problem with that is that it will then automatically
             * create a default (empty) 'sketch' tab. Even if we already loaded one.
             * Which means that data can get overwritten. So we want to ignore any
             * error caused by trying to load the undo/redo history.
             */
            e.printStackTrace();
          }
        }
      }

      return success;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Reload the current sketch... without saving. Useful for updating files that have been changed
   * outside of the editor.
   */
  public void reloadSketch() {
    getGlobalState().writeCodeDeletionDebugStatus("before reloadSketch()");

    loadSketch(getGlobalState().getSketchPath(), getGlobalState().getSketchLocationType());

    getGlobalState().writeCodeDeletionDebugStatus("after reloadSketch()");
  }

  /** Automatically save the sketch, whether it is to the sketchbook folder or to the temp folder */
  public void autoSave() {
    getGlobalState().writeCodeDeletionDebugStatus("before autoSave()");

    switch (getGlobalState().getSketchLocationType()) {
      case EXAMPLE:
      case LIBRARY_EXAMPLE:
        break;
      case SKETCHBOOK:
      case EXTERNAL:
      case TEMPORARY:
        saveSketch();
        break;
    }

    getGlobalState().writeCodeDeletionDebugStatus("end autoSave()");

    cancelAutoSave();
  }

  public void cancelAutoSave() {
    if (autoSaveTimerTask != null
        && !autoSaveTimerTask.isDone()
        && !autoSaveTimerTask.isCancelled()) {
      autoSaveTimerTask.cancel(false);
    }
  }

  public void scheduleAutoSave() {

    cancelAutoSave();

    long timeout =
        Long.parseLong(
            getGlobalState()
                .getPref(
                    "pref_key_autosave_timeout",
                    getGlobalState().getString(R.string.pref_autosave_timeout_default_value)));

    if (timeout != -1L) {
      autoSaveTimerTask = autoSaveTimer.schedule(autoSaveTimerAction, timeout, TimeUnit.SECONDS);
    }
  }

  public boolean cancelAutoCompile() {
    if (building) {
      return false;
    }
    if (autoCompileTask != null && !autoCompileTask.isDone() && !autoCompileTask.isCancelled()) {
      autoCompileTask.cancel(true);
    }
    return true;
  }

  public void scheduleAutoCompile(boolean immediate) {
    if (!cancelAutoCompile()) {

      return;
    }

    long timeout =
        Long.parseLong(
            getGlobalState()
                .getPref(
                    "pref_key_build_compile_timeout",
                    getGlobalState().getString(R.string.pref_build_compile_timeout_default_value)));

    if (timeout != -1L) {
      findViewById(R.id.auto_compile_placeholder)
          .setBackgroundColor(getResources().getColor(R.color.bar_overlay));
      autoCompileTask =
          autoCompileTimer.schedule(autoCompileAction, immediate ? 0 : timeout, TimeUnit.SECONDS);
    }
  }

  public void autoCompile() {
    runOnUiThread(
        () -> {
          if (isOldBuild()) {
            BuildContext context = BuildContext.create(getGlobalState());
            Build builder = new Build(getGlobalState(), context);

            autoCompileTimer.schedule(
                () -> {
                  long start = System.currentTimeMillis();

                  toggleAutoCompileIndicator(true);

                  runOnUiThread(
                      () -> {
                        findViewById(R.id.auto_compile_placeholder)
                            .setBackgroundColor(getResources().getColor(R.color.message_back));
                      });
                  builder.build("debug", getComponentTarget(), true);

                  toggleAutoCompileIndicator(false);

                  if (getGlobalState().getPref("build_output_verbose", false)) {
                    System.out.println(
                        String.format(
                            Locale.US, "Finished in %1$dms", System.currentTimeMillis() - start));
                  }
                },
                0,
                TimeUnit.SECONDS);
          } else {
            getGlobalState().getModularBuild().compile();
          }

          cancelAutoCompile();
        });
  }

  public void toggleAutoCompileIndicator(boolean enable) {
    runOnUiThread(
        () -> {
          ProgressBar progress = findViewById(R.id.auto_compile_progress);
          FrameLayout placeholder = findViewById(R.id.auto_compile_placeholder);

          progress.setVisibility(enable ? View.VISIBLE : View.GONE);
          placeholder.setVisibility(enable ? View.GONE : View.VISIBLE);

          if (android.os.Build.VERSION.SDK_INT >= 21) {
            progress.setProgressBackgroundTintList(
                ColorStateList.valueOf(getResources().getColor(android.R.color.transparent)));
            progress.setIndeterminateTintList(
                ColorStateList.valueOf(getResources().getColor(R.color.bar_overlay)));
          }
        });
  }

  /** Sets up the editor for a new sketch */
  public void newSketch() {

    getSupportActionBar().setTitle(getGlobalState().getSketchName());

    supportInvalidateOptionsMenu();

    tabs.clear();
    codePagerAdapter.notifyDataSetChanged();

    addDefaultTab(APDE.DEFAULT_SKETCH_TAB);

    selectCode(0);

    getSelectedSketchFile().clearUndoRedo();

    autoSave();

    getGlobalState()
        .putRecentSketch(
            getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());

    forceDrawerReload();

    scheduleAutoCompile(true);
  }

  /**
   * Loads a sketch
   *
   * @param sketchPath
   * @param sketchLocation
   * @return success
   */
  public boolean loadSketch(String sketchPath, APDE.SketchLocation sketchLocation) {
    if (sketchLocation == null) {

      return false;
    }

    File sketchLoc = getGlobalState().getSketchLocation(sketchPath, sketchLocation);
    boolean success;

    if (sketchLoc.exists() && sketchLoc.isDirectory()) {
      getGlobalState().selectSketch(sketchPath, sketchLocation);

      File[] files = sketchLoc.listFiles();

      for (SketchFile meta : tabs) {
        meta.disable();
      }

      tabs.clear();

      for (File file : files) {

        String[] folders = file.getPath().split("/");
        String[] parts = folders[folders.length - 1].split("\\.");

        if (parts.length != 2) continue;

        String prefix = parts[parts.length - 2];
        String suffix = parts[parts.length - 1];

        if (suffix.equals("pde")) {

          SketchFile meta = new SketchFile("");
          meta.readData(file.getAbsolutePath());
          meta.setTitle(prefix);
          meta.setExample(getGlobalState().isExample());

          addTabWithoutPagerUpdate(meta);

          meta.getFragment().setSketchFile(meta);
        } else if (suffix.equals("java")) {

          SketchFile meta = new SketchFile("");
          meta.readData(file.getAbsolutePath());
          meta.setTitle(prefix);
          meta.setSuffix(".java");
          meta.setExample(getGlobalState().isExample());

          addTabWithoutPagerUpdate(meta);

          meta.getFragment().setSketchFile(meta);
        }
      }

      codePagerAdapter.notifyDataSetChanged();

      success = true;

      selectCode(0);
    } else {
      success = false;
    }

    if (success) {

      ((FindReplace) getGlobalState().getPackageToToolTable().get(FindReplace.PACKAGE_NAME))
          .close();

      correctUndoRedoEnabled();

      getGlobalState().putRecentSketch(sketchLocation, sketchPath);

      if (!FLAG_FIRST_AUTO_COMPILE) {
        scheduleAutoCompile(true);
      }
    }

    getGlobalState().writeCodeDeletionDebugStatus("after loadSketch()");

    return success;
  }

  /** Saves the sketch to the sketchbook folder, creating a new subdirectory if necessary */
  public void saveSketch() {

    if (!externalStorageWritable()
        && (getGlobalState()
                .getSketchbookDrive()
                .type
                .equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)
            || getGlobalState()
                .getSketchbookDrive()
                .type
                .equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL))) {
      MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
      builder
          .setTitle(getResources().getText(R.string.external_storage_unavailable_dialog_title))
          .setMessage(getResources().getText(R.string.external_storage_unavailable_dialog_message))
          .setCancelable(false)
          .setPositiveButton(
              R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {}
              })
          .show();

      return;
    }

    getGlobalState().writeCodeDeletionDebugStatus("begin saveSketch()");

    boolean success = true;

    String sketchPath = getGlobalState().getSketchPath();

    File sketchLoc =
        getGlobalState().getSketchLocation(sketchPath, getGlobalState().getSketchLocationType());

    sketchLoc.mkdirs();

    if (getCodeCount() > 0) {

      for (int i = 0; i < tabs.size(); i++) {

        if (tabs.get(i).getFragment().getCodeEditText() != null) {
          if (tabs.get(i).getFragment().getCodeEditText().getText().length() == 0
              && tabs.get(i).getText().length() > 0) {

            getGlobalState()
                .writeDebugLog(
                    "saveSketch",
                    "Detected code deletion in tab '"
                        + tabs.get(i).getTitle()
                        + "', not overwriting.");
          } else {
            tabs.get(i).update(this, getGlobalState().getPref("pref_key_undo_redo", true));
          }
        }
      }

      for (SketchFile meta : tabs) {
        if (meta.enabled()) {

          if (!meta.writeData(sketchLoc.getPath() + "/")) {
            success = false;
          }
        }
      }

      if (success) {
        getGlobalState().selectSketch(sketchPath, getGlobalState().getSketchLocationType());

        forceDrawerReload();

        supportInvalidateOptionsMenu();

        message(getResources().getText(R.string.message_sketch_save_success));
        setSaved(true);
      } else {

        error(getResources().getText(R.string.message_sketch_save_failure));
      }
    } else {

      forceDrawerReload();

      message(getResources().getText(R.string.message_sketch_save_success));
      setSaved(true);
    }

    getGlobalState().writeCodeDeletionDebugStatus("after saveSketch()");
  }

  public void copyToSketchbook() {

    if (!externalStorageWritable()
        && (getGlobalState()
                .getSketchbookDrive()
                .type
                .equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)
            || getGlobalState()
                .getSketchbookDrive()
                .type
                .equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL))) {
      MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
      builder
          .setTitle(getResources().getText(R.string.external_storage_unavailable_dialog_title))
          .setMessage(getResources().getText(R.string.external_storage_unavailable_dialog_message))
          .setCancelable(false)
          .setPositiveButton(
              R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {}
              })
          .show();

      return;
    }

    File oldLoc = getGlobalState().getSketchLocation();

    String sketchPath = "/" + getGlobalState().getSketchName();

    File sketchLoc = getGlobalState().getSketchLocation(sketchPath, APDE.SketchLocation.SKETCHBOOK);

    sketchLoc.mkdirs();

    try {
      APDE.copyFile(oldLoc, sketchLoc);

      getGlobalState().putRecentSketch(APDE.SketchLocation.SKETCHBOOK, sketchPath);

      getGlobalState().selectSketch(sketchPath, APDE.SketchLocation.SKETCHBOOK);

      forceDrawerReload();

      supportInvalidateOptionsMenu();

      updateCodeAreaFocusable();

      message(getResources().getText(R.string.message_sketch_save_success));
      setSaved(true);
    } catch (IOException e) {

      error(getResources().getText(R.string.message_sketch_save_failure));
    }
  }

  public void updateCodeAreaFocusable() {
    for (SketchFile sketchFile : tabs) {
      sketchFile.setExample(false);
      sketchFile.forceReloadTextIfInitialized();
    }
  }

  public void moveToSketchbook() {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

    builder.setTitle(R.string.move_temp_to_sketchbook_title);
    builder.setMessage(
        String.format(
            Locale.US,
            getResources().getString(R.string.move_temp_to_sketchbook_message),
            getGlobalState().getSketchName()));

    builder.setPositiveButton(
        R.string.move_temp_to_sketchbook_button,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            APDE.SketchMeta source =
                new APDE.SketchMeta(
                    getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
            APDE.SketchMeta dest =
                new APDE.SketchMeta(APDE.SketchLocation.SKETCHBOOK, "/" + source.getName());

            if (getGlobalState().getSketchLocation(dest.getPath(), dest.getLocation()).exists()) {
              MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(EditorActivity.this);

              builder.setTitle(R.string.rename_sketch_failure_title);
              builder.setMessage(R.string.rename_move_folder_failure_message);

              builder.setPositiveButton(
                  getResources().getString(R.string.ok),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                  });

              builder.create().show();

              return;
            }

            getGlobalState().moveFolder(source, dest, EditorActivity.this);
            supportInvalidateOptionsMenu();
          }
        });

    builder.setNegativeButton(
        R.string.cancel,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {}
        });

    builder.create().show();
  }

  public void copyPrefs(String before, String after) {
    SharedPreferences old = getSharedPreferences(getGlobalState().getSketchName(), 0);
    SharedPreferences prefs = getSharedPreferences(after, 0);
    SharedPreferences.Editor ed = prefs.edit();

    for (Entry<String, ?> entry : old.getAll().entrySet()) {
      Object v = entry.getValue();
      String key = entry.getKey();

      if (v instanceof Boolean) ed.putBoolean(key, ((Boolean) v).booleanValue());
      else if (v instanceof Float) ed.putFloat(key, ((Float) v).floatValue());
      else if (v instanceof Integer) ed.putInt(key, ((Integer) v).intValue());
      else if (v instanceof Long) ed.putLong(key, ((Long) v).longValue());
      else if (v instanceof String) ed.putString(key, ((String) v));
    }

    ed.commit();
    old.edit().clear().commit();
  }

  private boolean validateSketchName(String name) {

    if (name.length() <= 0) {
      error(getResources().getText(R.string.sketch_name_invalid_no_char));
      return false;
    }

    char first = name.charAt(0);
    if ((first >= '0' && first <= '9') || first == '_') {
      error(getResources().getText(R.string.sketch_name_invalid_first_char));
      return false;
    }

    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c >= '0' && c <= '9') continue;
      if (c >= 'a' && c <= 'z') continue;
      if (c >= 'A' && c <= 'Z') continue;
      if (c == '_') continue;

      error(getResources().getText(R.string.sketch_name_invalid_char));
      return false;
    }

    return true;
  }

  public void launchDeleteSketchConfirmationDialog() {
    MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(this);

    alert.setTitle(
        String.format(
            Locale.US,
            getResources().getString(R.string.delete_sketch_dialog_title),
            getGlobalState().getSketchName()));
    alert.setMessage(
        String.format(
            Locale.US,
            getResources().getString(R.string.delete_sketch_dialog_message),
            getGlobalState().getSketchName()));

    alert.setPositiveButton(
        R.string.delete,
        (dialog, whichButton) -> {
          deleteSketch();

          getGlobalState().selectNewTempSketch();
          newSketch();

          toolbar.setTitle(getGlobalState().getSketchName());
        });

    alert.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {});

    alert.create().show();
  }

  /** Deletes the current sketch */
  public void deleteSketch() {

    File sketchFolder = getGlobalState().getSketchLocation();
    if (sketchFolder.isDirectory()) {
      try {

        APDE.deleteFile(sketchFolder);
      } catch (IOException e) {

        e.printStackTrace();
      }
    }
  }

  /**
   * Write text to a temp file
   *
   * @param filename
   * @param text
   * @return success
   */
  public boolean writeTempFile(String filename, String text) {
    BufferedOutputStream outputStream = null;
    boolean success;

    try {

      outputStream = new BufferedOutputStream(openFileOutput(filename, Context.MODE_PRIVATE));

      outputStream.write(text.getBytes());

      success = true;
    } catch (Exception e) {
      e.printStackTrace();

      success = false;

    } finally {

      if (outputStream != null) {
        try {
          outputStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    return success;
  }

  /**
   * Reads text from a temp file
   *
   * @param filename
   * @return the text
   */
  public String readTempFile(String filename) {
    BufferedInputStream inputStream = null;
    String output = "";

    try {

      inputStream = new BufferedInputStream(openFileInput(filename));

      byte[] contents = new byte[1024];
      int bytesRead = 0;

      while ((bytesRead = inputStream.read(contents)) != -1) {
        output += new String(contents, 0, bytesRead);
      }
    } catch (Exception e) {

    } finally {

      try {
        if (inputStream != null) {
          inputStream.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return output;
  }

  /** Reloads the navigation drawer */
  public void forceDrawerReload() {
    if (drawerOpen) {
      if (drawerSketchLocationType != null) {
        getSupportActionBar()
            .setSubtitle(
                drawerSketchLocationType.toReadableString(getGlobalState())
                    + drawerSketchPath
                    + "/");
      } else if (drawerRecentSketch) {
        getSupportActionBar()
            .setSubtitle(getResources().getString(R.string.drawer_folder_recent) + "/");
      } else {
        getSupportActionBar().setSubtitle(null);
      }
    } else {
      getSupportActionBar().setSubtitle(null);
    }

    ListView drawerList = (ListView) findViewById(R.id.drawer_list);

    ArrayList<FileNavigatorAdapter.FileItem> items;

    if (drawerSketchLocationType != null) {
      items =
          getGlobalState()
              .listSketchContainingFolders(
                  getGlobalState().getSketchLocation(drawerSketchPath, drawerSketchLocationType),
                  new String[] {APDE.LIBRARIES_FOLDER},
                  drawerSketchPath.length() > 0,
                  drawerSketchLocationType.equals(APDE.SketchLocation.SKETCHBOOK)
                      || drawerSketchLocationType.equals(APDE.SketchLocation.TEMPORARY),
                  new APDE.SketchMeta(drawerSketchLocationType, drawerSketchPath));
    } else {
      if (drawerRecentSketch) {
        items = getGlobalState().listRecentSketches();
      } else {
        items = new ArrayList<FileNavigatorAdapter.FileItem>();
        items.add(
            new FileNavigatorAdapter.FileItem(
                getResources().getString(R.string.drawer_folder_sketches),
                FileNavigatorAdapter.FileItemType.FOLDER));
        items.add(
            new FileNavigatorAdapter.FileItem(
                getResources().getString(R.string.drawer_folder_examples),
                FileNavigatorAdapter.FileItemType.FOLDER));
        items.add(
            new FileNavigatorAdapter.FileItem(
                getResources().getString(R.string.drawer_folder_library_examples),
                FileNavigatorAdapter.FileItemType.FOLDER));
        items.add(
            new FileNavigatorAdapter.FileItem(
                getResources().getString(R.string.drawer_folder_temporary),
                FileNavigatorAdapter.FileItemType.FOLDER));
        items.add(
            new FileNavigatorAdapter.FileItem(
                getResources().getString(R.string.drawer_folder_recent),
                FileNavigatorAdapter.FileItemType.FOLDER));
      }
    }

    int selected = -1;

    if (drawerSketchLocationType != null
        && drawerSketchLocationType.equals(getGlobalState().getSketchLocationType())
        && getGlobalState()
            .getSketchPath()
            .equals(drawerSketchPath + "/" + getGlobalState().getSketchName())) {

      selected = FileNavigatorAdapter.indexOfSketch(items, getGlobalState().getSketchName());
    } else if (drawerRecentSketch && !(getGlobalState().getRecentSketches().size() == 0)) {

      selected = 1;
    }

    FileNavigatorAdapter fileAdapter = new FileNavigatorAdapter(this, items, selected);
    try {
      drawerList.setAdapter(fileAdapter);
    } catch (SecurityException e) {

    }
    drawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

    drawerList.setOnItemLongClickListener(
        new AdapterView.OnItemLongClickListener() {
          @Override
          public boolean onItemLongClick(
              AdapterView<?> adapterView, View view, int position, long id) {
            return fileAdapter.onLongClickItem(view, position);
          }
        });
  }

  public void selectDrawerFolder(APDE.SketchLocation location) {
    drawerSketchLocationType = location;
    drawerSketchPath = "";
    drawerRecentSketch = false;

    forceDrawerReload();
  }

  public void selectDrawerRecent() {
    drawerSketchLocationType = null;
    drawerSketchPath = "";
    drawerRecentSketch = true;

    forceDrawerReload();
  }

  /**
   * @return the APDE application global state
   */
  public APDE getGlobalState() {
    return (APDE) getApplication();
  }

  protected ImageButton runStopMenuButton = null;
  protected boolean runStopMenuButtonAnimating = false;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.activity_editor, menu);
    prepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    prepareOptionsMenu(menu);
    return true;
  }

  public void prepareOptionsMenu(Menu menu) {
    runStopMenuButton = (ImageButton) menu.findItem(R.id.menu_run).getActionView();

    runStopMenuButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            if (runStopMenuButtonAnimating) {
              return;
            }

            if (!building) {
              runApplication();
            } else {
              stopApplication();
            }
          }
        });

    runStopMenuButton.setOnLongClickListener(
        new ImageButton.OnLongClickListener() {
          @Override
          public boolean onLongClick(View v) {
            Toast toast =
                Toast.makeText(
                    EditorActivity.this,
                    building ? R.string.editor_menu_stop_sketch : R.string.editor_menu_run_sketch,
                    Toast.LENGTH_SHORT);
            APDE.positionToast(toast, runStopMenuButton, getWindow(), 0, 0);
            toast.show();

            return true;
          }
        });

    if (android.os.Build.VERSION.SDK_INT >= 21) {
      runStopMenuButton.setImageDrawable(
          getResources()
              .getDrawable(
                  building & !runStopMenuButtonAnimating
                      ? R.drawable.ic_stop_vector
                      : R.drawable.ic_run_vector));
    } else {
      runStopMenuButton.setImageDrawable(
          getResources()
              .getDrawable(building ? R.drawable.ic_stop_white : R.drawable.ic_run_white));
    }

    if (drawerOpen) {

      menu.findItem(R.id.menu_run).setVisible(false);
      menu.findItem(R.id.menu_comp_select).setVisible(false);
      menu.findItem(R.id.menu_undo).setVisible(false);
      menu.findItem(R.id.menu_redo).setVisible(false);
      menu.findItem(R.id.menu_tab_delete).setVisible(false);
      menu.findItem(R.id.menu_tab_rename).setVisible(false);
      menu.findItem(R.id.menu_save).setVisible(false);
      menu.findItem(R.id.menu_delete).setVisible(false);
      menu.findItem(R.id.menu_rename).setVisible(false);
      menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
      menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
      menu.findItem(R.id.menu_new).setVisible(false);
      menu.findItem(R.id.menu_load).setVisible(false);
      menu.findItem(R.id.menu_tab_new).setVisible(false);
      menu.findItem(R.id.menu_tools).setVisible(false);
      menu.findItem(R.id.menu_sketch_properties).setVisible(false);

      getSupportActionBar().setTitle(R.string.apde_app_title);
    } else {
      if (getCodeCount() > 0) {

        menu.findItem(R.id.menu_run).setVisible(true);
        menu.findItem(R.id.menu_tab_delete).setVisible(true);
        menu.findItem(R.id.menu_tab_rename).setVisible(true);

        menu.findItem(R.id.menu_tools).setVisible(true);

        if (getGlobalState().isExample()) {
          menu.findItem(R.id.menu_undo).setVisible(false);
          menu.findItem(R.id.menu_redo).setVisible(false);
        } else {
          if (PreferenceManager.getDefaultSharedPreferences(this)
              .getBoolean("pref_key_undo_redo", true)) {
            menu.findItem(R.id.menu_undo).setVisible(true);
            menu.findItem(R.id.menu_redo).setVisible(true);
          } else {
            menu.findItem(R.id.menu_undo).setVisible(false);
            menu.findItem(R.id.menu_redo).setVisible(false);
          }
        }
      } else {

        menu.findItem(R.id.menu_run).setVisible(false);
        menu.findItem(R.id.menu_comp_select).setVisible(false);
        menu.findItem(R.id.menu_undo).setVisible(false);
        menu.findItem(R.id.menu_redo).setVisible(false);
        menu.findItem(R.id.menu_tab_delete).setVisible(false);
        menu.findItem(R.id.menu_tab_rename).setVisible(false);
        menu.findItem(R.id.menu_tools).setVisible(false);
      }

      SketchFile meta = getSelectedSketchFile();
      menu.findItem(R.id.menu_undo).setEnabled(meta != null && meta.canUndo());
      menu.findItem(R.id.menu_redo).setEnabled(meta != null && meta.canRedo());

      switch (getGlobalState().getSketchLocationType()) {
        case SKETCHBOOK:
          menu.findItem(R.id.menu_save).setVisible(true);
          menu.findItem(R.id.menu_delete).setVisible(true);
          menu.findItem(R.id.menu_rename).setVisible(true);
          menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
          menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
          break;
        case TEMPORARY:
          menu.findItem(R.id.menu_save).setVisible(true);
          menu.findItem(R.id.menu_delete).setVisible(true);
          menu.findItem(R.id.menu_rename).setVisible(false);
          menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
          menu.findItem(R.id.menu_move_to_sketchbook).setVisible(true);
          break;
        case EXTERNAL:
          menu.findItem(R.id.menu_save).setVisible(true);
          menu.findItem(R.id.menu_delete).setVisible(true);
          menu.findItem(R.id.menu_rename).setVisible(true);
          menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(true);
          menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
          break;
        case EXAMPLE:
        case LIBRARY_EXAMPLE:
          menu.findItem(R.id.menu_save).setVisible(false);
          menu.findItem(R.id.menu_delete).setVisible(false);
          menu.findItem(R.id.menu_rename).setVisible(false);
          menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(true);
          menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
          break;
      }

      menu.findItem(R.id.menu_new).setVisible(true);
      menu.findItem(R.id.menu_load).setVisible(true);
      menu.findItem(R.id.menu_tab_new).setVisible(true);
      menu.findItem(R.id.menu_sketch_properties).setVisible(true);

      getSupportActionBar().setTitle(getGlobalState().getSketchName());
    }

    menu.findItem(R.id.menu_tab_new).setVisible(false);
    menu.findItem(R.id.menu_tab_delete).setVisible(false);
    menu.findItem(R.id.menu_tab_rename).setVisible(false);

    menu.findItem(R.id.menu_save).setVisible(false);

    if (getCodeCount() <= 0 && !getGlobalState().isExample()) {
      menu.findItem(R.id.menu_tab_new).setVisible(true);
    }

    menu.findItem(R.id.menu_stop).setVisible(false);

    menu.findItem(R.id.menu_undo).setVisible(false);
    menu.findItem(R.id.menu_redo).setVisible(false);

    menu.findItem(R.id.menu_comp_select).setIcon(getComponentTarget().getIconId());
    menu.findItem(R.id.menu_comp_select).setTitle(getComponentTarget().getNameId());

    int alphaSelected = getResources().getInteger(R.integer.prop_menu_comp_select_alpha_selected);
    int alphaUnelected =
        getResources().getInteger(R.integer.prop_menu_comp_select_alpha_unselected);

    menu.findItem(R.id.menu_comp_select_app)
        .getIcon()
        .setAlpha(getComponentTarget() == ComponentTarget.APP ? alphaSelected : alphaUnelected);
    menu.findItem(R.id.menu_comp_select_wallpaper)
        .getIcon()
        .setAlpha(
            getComponentTarget() == ComponentTarget.WALLPAPER ? alphaSelected : alphaUnelected);
    menu.findItem(R.id.menu_comp_select_watchface)
        .getIcon()
        .setAlpha(
            getComponentTarget() == ComponentTarget.WATCHFACE ? alphaSelected : alphaUnelected);
    menu.findItem(R.id.menu_comp_select_vr)
        .getIcon()
        .setAlpha(getComponentTarget() == ComponentTarget.VR ? alphaSelected : alphaUnelected);
    menu.findItem(R.id.menu_comp_select_preview)
        .getIcon()
        .setAlpha(getComponentTarget() == ComponentTarget.PREVIEW ? alphaSelected : alphaUnelected);
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    drawerToggle.syncState();

    getGlobalState().writeCodeDeletionDebugStatus("onPostCreate()");
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    drawerToggle.onConfigurationChanged(newConfig);

    getGlobalState().writeCodeDeletionDebugStatus("onConfigurationChanged()");
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      RelativeLayout drawerLayout = (RelativeLayout) findViewById(R.id.drawer_wrapper);
      if (drawer.isDrawerOpen(Gravity.START)) {
        drawer.closeDrawer(drawerLayout);
      } else {
        drawer.openDrawer(drawerLayout);

        if (isSelectedCodeAreaInitialized()) {
          getSelectedCodeArea().setEnabled(false);
        }
        supportInvalidateOptionsMenu();
      }
      return true;
    }
    if (id == R.id.menu_comp_select_app) {
      setComponentTarget(ComponentTarget.APP);
      return true;
    }
    if (id == R.id.menu_comp_select_wallpaper) {
      setComponentTarget(ComponentTarget.WALLPAPER);
      return true;
    }
    if (id == R.id.menu_comp_select_watchface) {
      setComponentTarget(ComponentTarget.WATCHFACE);
      return true;
    }
    if (id == R.id.menu_comp_select_vr) {
      setComponentTarget(ComponentTarget.VR);
      return true;
    }
    if (id == R.id.menu_comp_select_preview) {
      setComponentTarget(ComponentTarget.PREVIEW);
      return true;
    }
    if (id == R.id.menu_undo) {
      tabs.get(getSelectedCodeIndex()).undo(this);
      return true;
    }
    if (id == R.id.menu_redo) {
      tabs.get(getSelectedCodeIndex()).redo(this);
      return true;
    }
    if (id == R.id.menu_save) {
      saveSketch();
      return true;
    }
    if (id == R.id.menu_rename) {
      launchRenameSketch();
      return true;
    }
    if (id == R.id.menu_copy_to_sketchbook) {
      copyToSketchbook();
      return true;
    }
    if (id == R.id.menu_move_to_sketchbook) {
      moveToSketchbook();
      return true;
    }
    if (id == R.id.menu_new) {
      createNewSketch();
      return true;
    }
    if (id == R.id.menu_load) {
      loadSketch();
      return true;
    }
    if (id == R.id.menu_delete) {
      launchDeleteSketchConfirmationDialog();
      return true;
    }
    if (id == R.id.menu_settings) {
      launchSettings();
      return true;
    }
    if (id == R.id.menu_tab_new) {
      addTabWithDialog();
      return true;
    }
    if (id == R.id.menu_tab_rename) {
      renameTab();
      return true;
    }
    if (id == R.id.menu_tab_delete) {
      deleteTab();
      return true;
    }
    if (id == R.id.menu_tools) {
      launchTools();
      return true;
    }
    if (id == R.id.menu_sketch_properties) {
      launchSketchProperties();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  public void toggleCharInserts() {

    if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
            .getBoolean("char_inserts", true)
        || !keyboardVisible) {
      return;
    }

    if (charInserts) {
      hideCharInserts();
    } else {

      reloadCharInserts();
      showCharInserts();
    }
  }

  public void toggleProblemOverview() {
    if (keyboardVisible) {
      return;
    }

    if (problemOverview) {
      consoleWrapperPager.setCurrentItem(0);
      problemOverview = false;
    } else {
      consoleWrapperPager.setCurrentItem(1);
      problemOverview = true;
    }
  }

  protected void showCharInserts() {
    TextView messageView = findViewById(R.id.message);
    HorizontalScrollView charInsertTray = findViewById(R.id.char_insert_tray);

    View buffer = findViewById(R.id.buffer);
    View sep = findViewById(R.id.toggle_char_inserts_separator);
    View wrapper = findViewById(R.id.toggle_wrapper);

    toggleCharInserts.setImageResource(
        messageType != MessageType.MESSAGE
            ? R.drawable.ic_caret_right_white
            : R.drawable.ic_caret_right_black);
    toggleProblemOverview.setImageResource(
        messageType != MessageType.MESSAGE
            ? R.drawable.problem_overview_white_unfilled
            : R.drawable.problem_overview_black_unfilled);

    int total = buffer.getWidth() - sep.getWidth() - wrapper.getWidth();

    RotateAnimation rotate =
        new RotateAnimation(
            180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    rotate.setInterpolator(new AccelerateDecelerateInterpolator());
    rotate.setRepeatCount(0);
    rotate.setDuration(200);

    ResizeAnimation messageAnimation =
        new ResizeAnimation<LinearLayout>(
            messageView,
            ResizeAnimation.DEFAULT,
            ResizeAnimation.DEFAULT,
            0,
            ResizeAnimation.DEFAULT,
            true,
            false);
    ResizeAnimation<LinearLayout> charInsertAnimation =
        new ResizeAnimation<>(charInsertTray, 0, buffer.getHeight(), total, buffer.getHeight());

    Animation.AnimationListener listener =
        new Animation.AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {
            if (!keyboardVisible) {}
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        };

    messageAnimation.setAnimationListener(listener);
    charInsertAnimation.setAnimationListener(listener);

    messageView.startAnimation(messageAnimation);
    charInsertTray.startAnimation(charInsertAnimation);

    toggleCharInserts.startAnimation(rotate);

    charInserts = true;
  }

  protected void hideCharInserts() {
    if (!(keyboardVisible && charInserts)) {

      return;
    }

    TextView messageView = findViewById(R.id.message);
    HorizontalScrollView charInsertTray = findViewById(R.id.char_insert_tray);

    View buffer = findViewById(R.id.buffer);
    View sep = findViewById(R.id.toggle_char_inserts_separator);
    View wrapper = findViewById(R.id.toggle_wrapper);

    toggleCharInserts.setImageResource(
        messageType != MessageType.MESSAGE
            ? R.drawable.ic_caret_left_white
            : R.drawable.ic_caret_left_black);
    toggleProblemOverview.setImageResource(
        messageType != MessageType.MESSAGE
            ? R.drawable.problem_overview_white_unfilled
            : R.drawable.problem_overview_black_unfilled);

    int total = buffer.getWidth() - sep.getWidth() - wrapper.getWidth();

    RotateAnimation rotate =
        new RotateAnimation(
            180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    rotate.setInterpolator(new AccelerateDecelerateInterpolator());
    rotate.setRepeatCount(0);
    rotate.setDuration(200);

    messageView.startAnimation(
        new ResizeAnimation<LinearLayout>(
            messageView, 0, buffer.getHeight(), total, buffer.getHeight()));
    charInsertTray.startAnimation(
        new ResizeAnimation<LinearLayout>(
            charInsertTray,
            ResizeAnimation.DEFAULT,
            ResizeAnimation.DEFAULT,
            0,
            ResizeAnimation.DEFAULT));
    toggleCharInserts.startAnimation(rotate);

    charInserts = false;
  }

  public void hideCharInsertsNoAnimation(boolean disable) {
    View messageView = findViewById(R.id.message);
    View charInsertTray = findViewById(R.id.char_insert_tray);

    toggleCharInserts.setImageResource(
        messageType != MessageType.MESSAGE
            ? R.drawable.ic_caret_left_white
            : R.drawable.ic_caret_left_black);
    toggleProblemOverview.setImageResource(
        messageType != MessageType.MESSAGE
            ? R.drawable.problem_overview_white_unfilled
            : R.drawable.problem_overview_black_unfilled);
    messageView.setVisibility(View.VISIBLE);
    charInsertTray.setVisibility(View.GONE);

    messageView.getLayoutParams().width = -1;
    messageView.requestLayout();

    if (disable) {
      charInserts = false;
    }
  }

  /** Set up the character inserts tray. */
  public void reloadCharInserts() {
    if (!keyboardVisible) {
      return;
    }

    if (message == -1) {
      message = findViewById(R.id.buffer).getHeight();
    }

    LinearLayout container = ((LinearLayout) findViewById(R.id.char_insert_tray_list));

    container.removeAllViews();

    String[] chars;

    if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        .getBoolean("char_inserts_include_numbers", true))
      chars =
          new String[] {
            "\u2192", ";", ".", ",", "{", "}", "(", ")", "=", "*", "/", "+", "-", "&", "|", "!",
            "[", "]", "<", ">", "\"", "'", "\\", "_", "?", ":", "%", "@", "#", "1", "2", "3", "4",
            "5", "6", "7", "8", "9", "0"
          };
    else
      chars =
          new String[] {
            "\u2192", ";", ".", ",", "{", "}", "(", ")", "=", "*", "/", "+", "-", "&", "|", "!",
            "[", "]", "<", ">", "\"", "'", "\\", "_", "?", ":", "%", "@", "#"
          };

    int keyboardID = 0;

    for (String c : chars) {
      Button button = (Button) LayoutInflater.from(this).inflate(R.layout.char_insert_button, null);
      button.setText(c);
      button.setTextColor(
          getResources()
              .getColor(
                  messageType != MessageType.MESSAGE
                      ? R.color.char_insert_button_light
                      : R.color.char_insert_button));
      button.setLayoutParams(
          new LinearLayout.LayoutParams(
              (int)
                  TypedValue.applyDimension(
                      TypedValue.COMPLEX_UNIT_DIP, 25, getResources().getDisplayMetrics()),
              message));
      button.setPadding(0, 0, 0, 0);

      button.setSoundEffectsEnabled(
          getGlobalState().getPref("char_inserts_key_press_sound", false));

      container.addView(button);

      button.setOnClickListener(
          view -> {
            KeyEvent event =
                c.equals("\u2192")
                    ? new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)
                    : new KeyEvent(SystemClock.uptimeMillis(), c, keyboardID, 0);

            boolean dispatched = false;

            if (extraHeaderView != null) {

              EditText findTextField = extraHeaderView.findViewById(R.id.find_replace_find_text);
              EditText replaceTextField =
                  extraHeaderView.findViewById(R.id.find_replace_replace_text);

              if (findTextField != null) {
                if (findTextField.hasFocus()) {
                  findTextField.dispatchKeyEvent(event);
                  dispatched = true;
                } else {
                  if (replaceTextField != null && replaceTextField.hasFocus()) {
                    replaceTextField.dispatchKeyEvent(event);
                    dispatched = true;
                  }
                }
              }
            }

            if (!dispatched) {
              getSelectedCodeArea().dispatchKeyEvent(event);
            }

            if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("pref_vibrate", true))
              ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(10);
          });
    }
  }

  public class ProblemOverviewListAdapter extends ArrayAdapter<CompilerProblem> {
    public ProblemOverviewListAdapter(
        @NonNull Context context, int resource, List<CompilerProblem> items) {
      super(context, resource, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;

      if (view == null) {
        view = LayoutInflater.from(getContext()).inflate(R.layout.problem_overview_list_item, null);
      }

      CompilerProblem problem = getItem(position);

      if (view != null && view instanceof LinearLayout && problem != null) {
        LinearLayout container = (LinearLayout) view;
        TextView problemText = container.findViewById(R.id.problem_overview_list_item_problem_text);
        ImageView problemIcon =
            container.findViewById(R.id.problem_overview_list_item_problem_icon);

        problemText.setText(getProblemOverviewDescription(getContext(), problem));

        ImageViewCompat.setImageTintList(
            problemIcon,
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    getContext(), problem.isError() ? R.color.error_back : R.color.warning_back)));
      }

      return view;
    }
  }

  /**
   * Build the problem overview description from a compiler problem. This is used in the problem
   * overview and in the message area when the problem is shown there.
   *
   * @param context
   * @param problem
   * @return
   */
  protected static SpannableStringBuilder getProblemOverviewDescription(
      Context context, CompilerProblem problem) {
    SpannableStringBuilder text = new SpannableStringBuilder();

    if (problem.sketchFile != null) {
      text.append(problem.sketchFile.getTitle());
      text.append(" [");
      text.append(Integer.toString(problem.line + 1));
      text.append("]: ");
      text.setSpan(
          new ForegroundColorSpan(ContextCompat.getColor(context, android.R.color.darker_gray)),
          0,
          text.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    text.append(problem.message);

    return text;
  }

  private boolean isOldBuild() {

    return true;
  }

  /**
   * Builds and launches the current sketch This CAN be called multiple times without breaking
   * anything
   */
  private void runApplication() {
    switch (getGlobalState().getSketchLocationType()) {
      case LIBRARY_EXAMPLE:
        break;
      case TEMPORARY:
        saveSketch();
        break;
    }

    if (building) {
      return;
    }

    if (getGlobalState().getPref("pref_build_check_screen_overlay", true)
        && !FLAG_SCREEN_OVERLAY_INSTALL_ANYWAY
        && !(getComponentTarget() == ComponentTarget.PREVIEW
            && SketchPreviewerBuilder.isPreviewerInstalled(this))
        && !checkScreenOverlay()) {
      return;
    }

    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

    imm.hideSoftInputFromWindow((findViewById(R.id.content)).getWindowToken(), 0);

    ((TextView) findViewById(R.id.console)).setText("");

    if (isOldBuild()) {
      BuildContext context = BuildContext.create(getGlobalState());
      Build builder = new Build(getGlobalState(), context);

      Runnable task =
          () -> {
            building = true;
            changeRunStopIcon(true);

            long start = System.currentTimeMillis();

            toggleAutoCompileIndicator(true);

            builder.build("debug", getComponentTarget());

            toggleAutoCompileIndicator(false);

            System.out.println(
                String.format(Locale.US, "Finished in %1$dms", System.currentTimeMillis() - start));

            for (int i = 0; i < 10; i++) {
              System.out.println("");
            }

            changeRunStopIcon(false);
            building = false;
          };

      cancelAutoCompile();
      autoCompileTask = autoCompileTimer.schedule(task, 0, TimeUnit.SECONDS);
    } else {
      building = true;
      changeRunStopIcon(true);

      getGlobalState()
          .getModularBuild()
          .build(
              new ModularBuild.ContextualizedOnCompleteListener() {
                @Override
                public boolean onComplete(boolean success) {
                  runOnUiThread(
                      () -> {
                        changeRunStopIcon(false);
                        building = false;
                      });

                  return true;
                }
              });
    }

    if (android.os.Build.VERSION.SDK_INT >= 21) {
      runStopMenuButtonAnimating = true;
    }
  }

  /**
   * Stops the current sketch's build process This CAN be called multiple times without breaking
   * anything
   */
  private void stopApplication() {
    if (building) {
      Build.halt();
    }
  }

  public void changeRunStopIcon(boolean run) {
    runStopMenuButton.post(
        () -> {
          if (android.os.Build.VERSION.SDK_INT >= 21) {
            AnimatedVectorDrawable anim =
                (AnimatedVectorDrawable)
                    getDrawable(run ? R.drawable.run_to_stop : R.drawable.stop_to_run);
            runStopMenuButton.setImageDrawable(anim);
            anim.start();
            runStopMenuButtonAnimating = true;

            runStopMenuButton.postDelayed(
                () -> {
                  supportInvalidateOptionsMenu();
                  runStopMenuButtonAnimating = false;
                },
                getResources().getInteger(R.integer.run_stop_animation_duration));
          } else {
            supportInvalidateOptionsMenu();
          }
        });
  }

  /**
   * Set to the most recent value of MotionEvent.FLAG_WINDOW_IS_OBSCURED
   *
   * <p>This will be true if a screen overlay (e.g. Twilight, Lux, or similar app) is currently
   * being drawn over the screen. Android security measures prevent app installation if a screen
   * overlay is being drawn, so we need to let the user know and stop the build so that we don't get
   * "I can't press the install button!" emails.
   *
   * <p>This is the exact same detection method used by the Android system, so there are no corner
   * cases. See the link below for the source code of Android's implementation:
   *
   * <p>http:
   */
  private boolean isTouchObscured = false;

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    /*
     * See comments for isTouchObscured above.
     */

    isTouchObscured = (event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0;
    return super.dispatchTouchEvent(event);
  }

  /**
   * Check to see if we can proceed with the build. If a screen overlay is in place, then the
   * Android system will prevent the package manager from installing the sketch, so show a warning
   * message to the user.
   *
   * @return whether or not to proceed with the build
   */
  public boolean checkScreenOverlay() {
    if (isTouchObscured) {
      MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

      builder.setTitle(R.string.screen_overlay_dialog_title);

      LinearLayout layout =
          (LinearLayout)
              LayoutInflater.from(this).inflate(R.layout.screen_overlay_dialog, null, false);
      CheckBox dontShowAgain =
          (CheckBox) layout.findViewById(R.id.screen_overlay_dialog_dont_show_again);
      builder.setView(layout);

      builder.setPositiveButton(
          R.string.screen_overlay_dialog_install_anyway,
          (dialogInterface, i) -> {
            if (dontShowAgain.isChecked()) {

              getGlobalState().putPref("pref_build_check_screen_overlay", false);
            } else {

              FLAG_SCREEN_OVERLAY_INSTALL_ANYWAY = true;
            }

            runApplication();
          });

      builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {});

      builder.setNeutralButton(
          R.string.export_signed_package_long_info_button,
          (dialogInterface, i) ->
              startActivity(
                  new Intent(
                      Intent.ACTION_VIEW,
                      Uri.parse(getResources().getString(R.string.screen_overlay_info_wiki_url)))));

      builder.show();

      return false;
    } else {
      return true;
    }
  }

  private void updateMessageArea(String msg, MessageType type) {

    ((TextView) findViewById(R.id.message)).setText(msg);
    switch (type) {
      case MESSAGE:
        colorMessageAreaMessage();
        break;
      case ERROR:
        colorMessageAreaError();
        break;
      case WARNING:
        colorMessageAreaWarning();
        break;
    }
    messageType = type;

    correctMessageAreaHeight();
  }

  /**
   * Writes a message to the message area
   *
   * @param msg
   */
  public void message(String msg) {
    updateMessageArea(msg, MessageType.MESSAGE);
  }

  /**
   * Writes a message to the message area from a non-UI thread (this is used primarily from the
   * build thread)
   *
   * @param msg
   */
  public void messageExt(String msg) {
    runOnUiThread(() -> message(msg));
  }

  /**
   * Writes a message to the message area This is a convenience method for message(String)
   *
   * @param msg
   */
  public void message(CharSequence msg) {
    message(msg.toString());
  }

  /**
   * Writes an error message to the message area
   *
   * @param msg
   */
  public void error(String msg) {
    updateMessageArea(msg, MessageType.ERROR);
  }

  /**
   * Writes an error message to the message area from a non-UI thread (this is used primarily from
   * the build thread)
   *
   * @param msg
   */
  public void errorExt(String msg) {
    runOnUiThread(() -> error(msg));
  }

  /**
   * Writes an error message to the message area This is a convenience method for error(String)
   *
   * @param msg
   */
  public void error(CharSequence msg) {
    error(msg.toString());
  }

  public void warning(String msg) {
    updateMessageArea(msg, MessageType.WARNING);
  }

  public void warningExt(String msg) {
    runOnUiThread(() -> warning(msg));
  }

  protected void colorMessageAreaMessage() {

    findViewById(R.id.buffer).setBackgroundColor(getResources().getColor(R.color.message_back));
    ((TextView) findViewById(R.id.message))
        .setTextColor(getResources().getColor(R.color.message_text));

    toggleCharInserts.setImageResource(
        charInserts ? R.drawable.ic_caret_right_black : R.drawable.ic_caret_left_black);
    toggleProblemOverview.setImageResource(
        charInserts
            ? R.drawable.problem_overview_black_unfilled
            : R.drawable.problem_overview_black_unfilled);

    findViewById(R.id.toggle_char_inserts_separator)
        .setBackgroundColor(getResources().getColor(R.color.toggle_char_inserts_separator));

    LinearLayout charInsertTrayList = (LinearLayout) findViewById(R.id.char_insert_tray_list);
    for (int i = 0; i < charInsertTrayList.getChildCount(); i++) {
      ((Button) charInsertTrayList.getChildAt(i))
          .setTextColor(getResources().getColor(R.color.char_insert_button));
    }
  }

  protected void colorMessageAreaError() {

    findViewById(R.id.buffer).setBackgroundColor(getResources().getColor(R.color.error_back));
    ((TextView) findViewById(R.id.message))
        .setTextColor(getResources().getColor(R.color.error_text));

    toggleCharInserts.setImageResource(
        charInserts ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_left_white);
    toggleProblemOverview.setImageResource(
        charInserts
            ? R.drawable.problem_overview_white_unfilled
            : R.drawable.problem_overview_white_unfilled);

    findViewById(R.id.toggle_char_inserts_separator)
        .setBackgroundColor(getResources().getColor(R.color.toggle_char_inserts_separator_light));

    LinearLayout charInsertTrayList = (LinearLayout) findViewById(R.id.char_insert_tray_list);
    for (int i = 0; i < charInsertTrayList.getChildCount(); i++) {
      ((Button) charInsertTrayList.getChildAt(i))
          .setTextColor(getResources().getColor(R.color.char_insert_button_light));
    }
  }

  protected void colorMessageAreaWarning() {

    findViewById(R.id.buffer).setBackgroundColor(getResources().getColor(R.color.warning_back));
    ((TextView) findViewById(R.id.message))
        .setTextColor(getResources().getColor(R.color.warning_text));

    toggleCharInserts.setImageResource(
        charInserts ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_left_white);
    toggleProblemOverview.setImageResource(
        charInserts
            ? R.drawable.problem_overview_white_unfilled
            : R.drawable.problem_overview_white_unfilled);

    findViewById(R.id.toggle_char_inserts_separator)
        .setBackgroundColor(getResources().getColor(R.color.toggle_char_inserts_separator_light));

    LinearLayout charInsertTrayList = (LinearLayout) findViewById(R.id.char_insert_tray_list);
    for (int i = 0; i < charInsertTrayList.getChildCount(); i++) {
      ((Button) charInsertTrayList.getChildAt(i))
          .setTextColor(getResources().getColor(R.color.char_insert_button_light));
    }
  }

  protected void correctMessageAreaHeight() {
    TextView messageArea = findViewById(R.id.message);
    View buffer = findViewById(R.id.buffer);

    buffer.requestLayout();

    buffer.post(
        () -> {
          int totalWidth = findViewById(R.id.message_char_insert_wrapper).getWidth();

          message =
              getTextViewHeight(
                  getApplicationContext(),
                  messageArea.getText().toString(),
                  messageArea.getTextSize(),
                  totalWidth,
                  messageArea.getPaddingTop());

          int singleLineHeight =
              getTextViewHeight(
                  getApplicationContext(),
                  "",
                  messageArea.getTextSize(),
                  totalWidth,
                  messageArea.getPaddingTop());

          buffer.getLayoutParams().height = message;

          View console = findViewById(R.id.console_wrapper);
          View content = findViewById(R.id.content);
          FrameLayout autoCompileProgress = findViewById(R.id.auto_compile_progress_wrapper);

          if (isSelectedCodeAreaInitialized()) {
            int consoleCodeHeight =
                content.getHeight()
                    - message
                    - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)
                    - tabBarContainer.getHeight()
                    - autoCompileProgress.getHeight();
            int consoleHeight = consoleCodeHeight - codePager.getHeight();

            if (consoleHeight < 0 || keyboardVisible) {
              codePager.setLayoutParams(
                  new LinearLayout.LayoutParams(
                      FrameLayout.LayoutParams.MATCH_PARENT,
                      content.getHeight()
                          - message
                          - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)
                          - tabBarContainer.getHeight()
                          - autoCompileProgress.getHeight()));
              console.setLayoutParams(
                  new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            } else {
              console.setLayoutParams(
                  new LinearLayout.LayoutParams(
                      LinearLayout.LayoutParams.MATCH_PARENT,
                      content.getHeight()
                          - codePager.getHeight()
                          - message
                          - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)
                          - tabBarContainer.getHeight()
                          - autoCompileProgress.getHeight()));
            }
          }

          buffer.getLayoutParams().height = message;
          messageArea.getLayoutParams().height = message;

          setViewLayoutParams(toggleCharInserts, singleLineHeight, message);

          setViewLayoutParams(toggleProblemOverview, singleLineHeight, message);

          setViewLayoutParams(findViewById(R.id.toggle_wrapper), singleLineHeight, message);

          if (charInserts) {

            findViewById(R.id.char_insert_tray).getLayoutParams().height = message;
            LinearLayout charInsertContainer = findViewById(R.id.char_insert_tray_list);
            charInsertContainer.getLayoutParams().height = message;
            for (int i = 0; i < charInsertContainer.getChildCount(); i++) {
              View view = charInsertContainer.getChildAt(i);
              LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
              params.height = message;
              view.setLayoutParams(params);
            }
          }

          buffer.requestLayout();
        });
  }

  private void setViewLayoutParams(View view, int width, int height) {
    view.getLayoutParams().width = width;
    view.getLayoutParams().height = height;
    view.requestLayout();
  }

  public void initCodeAreaAndConsoleDimensions() {

    codePager.getLayoutParams().height = codePager.getHeight();
    getConsoleWrapper().getLayoutParams().height = getConsoleWrapper().getHeight();
    codePager.requestLayout();
    getConsoleWrapper().requestLayout();
  }

  /** Fix inconsistencies in the vertical distribution of the content area views */
  public void refreshMessageAreaLocation() {

    View content = findViewById(R.id.content);
    View console = findViewById(R.id.console_wrapper);
    View code = getSelectedCodeAreaScroller();
    TextView messageArea = (TextView) findViewById(R.id.message);
    FrameLayout autoCompileProgress = findViewById(R.id.auto_compile_progress_wrapper);

    if (firstResize) {

      code.setLayoutParams(
          new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, code.getHeight()));
      console.setLayoutParams(
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, console.getHeight()));

      firstResize = false;
    }

    messageArea.requestLayout();

    messageArea.post(
        () -> {
          message =
              getTextViewHeight(
                  getApplicationContext(),
                  messageArea.getText().toString(),
                  messageArea.getTextSize(),
                  findViewById(R.id.message_char_insert_wrapper).getWidth(),
                  messageArea.getPaddingTop());

          int consoleSize =
              content.getHeight()
                  - code.getHeight()
                  - message
                  - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)
                  - tabBarContainer.getHeight()
                  - autoCompileProgress.getHeight();

          if (consoleSize < 0 || consoleWasHidden || keyboardVisible) {
            console.setLayoutParams(
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
            code.setLayoutParams(
                new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    content.getHeight()
                        - message
                        - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)
                        - tabBarContainer.getHeight()
                        - autoCompileProgress.getHeight()));

            consoleWasHidden = true;
          } else {
            console.setLayoutParams(
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, consoleSize));

            consoleWasHidden = false;
          }
        });
  }

  private static int getTextViewHeight(
      Context context, String text, float textSize, int deviceWidth, int padding) {
    TextView textView = new TextView(context);
    textView.setText(text);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    textView.setPadding(padding, padding, padding, padding);

    int widthMeasureSpec = MeasureSpec.makeMeasureSpec(deviceWidth, MeasureSpec.AT_MOST);
    int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

    textView.measure(widthMeasureSpec, heightMeasureSpec);
    return textView.getMeasuredHeight();
  }

  /**
   * Highlights a code line (and opens the corresponding tab) in the code area from a non-UI thread
   * (this is used primarily from the build thread)
   *
   * @param tab
   * @param line
   */
  public void highlightLineExt(int tab, int line) {
    highlightTextExt(tab, line, 0, -1);
  }

  public void highlightTextExt(int tab, int line, int pos, int length) {
    runOnUiThread(
        new Runnable() {
          public void run() {

            if (tab != -1 && tab < tabs.size()) {
              selectCode(tab);
            }

            CodeEditText code = getSelectedCodeArea();

            if (code == null) {
              return;
            }

            int lineStart = code.offsetForLine(line);
            int lineStop = code.offsetForLineEnd(line);

            int start = Math.max(lineStart + pos, lineStart);
            int stop = length == -1 ? lineStop : Math.min(start + length, lineStop);

            code.requestFocus();

            code.post(
                () -> {
                  if (start >= 0
                      && start < code.length()
                      && stop >= 0
                      && stop < code.length()
                      && stop >= start) {

                    code.setSelection(start, stop);
                  }
                });
          }
        });
  }

  protected void toggleCharInsertsProblemOverviewButton(boolean problemOverview, boolean animate) {
    View fadeIn = problemOverview ? toggleProblemOverview : toggleCharInserts;
    View fadeOut = problemOverview ? toggleCharInserts : toggleProblemOverview;

    animate = false;

    if (animate) {
      int animTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
      int theta = 90;

      fadeIn.setAlpha(0.0f);
      fadeIn.setVisibility(View.VISIBLE);

      fadeIn.setRotation(-theta);

      fadeIn
          .animate()
          .alpha(1.0f)
          .rotationBy(theta)
          .setDuration(animTime)
          .setInterpolator(new DecelerateInterpolator())
          .setListener(null);
      fadeOut
          .animate()
          .alpha(0.0f)
          .rotationBy(theta)
          .setDuration(animTime)
          .setInterpolator(new AccelerateInterpolator())
          .setListener(
              new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                  fadeOut.setVisibility(View.GONE);
                  fadeOut.setRotation(0);
                }
              });
    } else {
      fadeOut.setVisibility(View.GONE);
      fadeIn.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Update the list of compiler problems displayed for the current sketch. Called by build upon the
   * completion of ECJ.
   */
  public void showProblems(List<CompilerProblem> problems) {
    compilerProblems.clear();
    compilerProblems.addAll(problems);

    int i = 0;
    for (SketchFile sketchFile : getSketchFiles()) {

      sketchFile.setCompilerProblems(compilerProblems, i);
      if (sketchFile.getFragment() != null && sketchFile.getFragment().getCodeEditText() != null) {
        runOnUiThread(sketchFile.getFragment().getCodeEditText()::invalidate);
      }
      i++;
    }

    Collections.sort(
        compilerProblems,
        (a, b) -> {
          if (a.isError() == b.isError()) {
            return 0;
          } else {
            return a.isError() ? -1 : 1;
          }
        });

    if (compilerProblems.size() > 0 && !getGlobalState().isExample()) {
      if (compilerProblems.get(0).isError()) {

        errorExt(compilerProblems.get(0).getMessage());
      } else {

        warningExt(compilerProblems.get(0).getMessage());
      }
    }

    if (compilerProblems.isEmpty() && messageType != MessageType.MESSAGE) {

      messageExt("");
    }

    runOnUiThread(
        () -> {
          problemOverviewListAdapter.notifyDataSetChanged();

          boolean hasItems = compilerProblems.size() > 0;
          problemOverviewList.setVisibility(hasItems ? View.VISIBLE : View.GONE);
          findViewById(R.id.problem_overview_list_empty_message)
              .setVisibility(hasItems ? View.GONE : View.VISIBLE);
        });
  }

  protected void addTabWithoutPagerUpdate(SketchFile sketchFile) {
    tabs.add(sketchFile);
  }

  public void addTab(SketchFile sketchFile) {
    tabs.add(sketchFile);
    codePagerAdapter.notifyDataSetChanged();
    scheduleAutoCompile(true);
  }

  public void onTabReselected(View view) {
    if (!drawerOpen && !getGlobalState().isExample()) {
      PopupMenu popup = new PopupMenu(getGlobalState().getEditor(), view);

      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.tab_actions, popup.getMenu());

      popup.setOnMenuItemClickListener(
          new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
              int id = item.getItemId();
              if (id == R.id.menu_tab_new) {
                addTabWithDialog();
                return true;
              }
              if (id == R.id.menu_tab_rename) {
                renameTab();
                return true;
              }
              if (id == R.id.menu_tab_delete) {
                deleteTab();
                return true;
              }

              return false;
            }
          });
      popup.show();
    }
  }

  /** Creates a user input dialog for adding a new tab */
  private void addTabWithDialog() {
    createInputDialog(
        getResources().getString(R.string.tab_new_dialog_title),
        getResources().getString(R.string.tab_new_dialog_message),
        "",
        NEW_TAB);
  }

  /**
   * Adds a default tab to the tab bar
   *
   * @param title
   */
  private void addDefaultTab(String title) {

    addTab(new SketchFile(title));
  }

  /**
   * Adds a tab to the tab bar
   *
   * @param title
   */
  private void addTab(String title) {

    addTab(new SketchFile(title));

    selectCode(getCodeCount() - 1);
  }

  /** Creates a user input dialog for renaming the current tab */
  private void renameTab() {
    if (tabs.size() > 0 && !getGlobalState().isExample())
      createInputDialog(
          getResources().getString(R.string.tab_rename_dialog_title),
          getResources().getString(R.string.tab_rename_dialog_message),
          getSelectedSketchFile().getTitle(),
          RENAME_TAB);
  }

  /** Creates a user input dialog for deleting the current tab */
  private void deleteTab() {
    if (getGlobalState().isExample()) return;

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder
        .setTitle(R.string.tab_delete_dialog_title)
        .setMessage(R.string.tab_delete_dialog_message)
        .setNegativeButton(
            R.string.cancel,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {}
            })
        .setPositiveButton(
            R.string.delete,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                deleteTabContinue();
              }
            })
        .show();
  }

  /**
   * Deletes a file in the sketch folder
   *
   * @param filename
   * @return success
   */
  private boolean deleteLocalFile(String filename) {

    File sketchLoc = getGlobalState().getSketchLocation();

    File file = new File(sketchLoc + "/", filename);

    if (file.exists()) {
      file.delete();

      return true;
    }

    return false;
  }

  /** Called internally from delete tab dialog */
  private void deleteTabContinue() {
    if (tabs.size() > 0) {

      deleteLocalFile(getSelectedSketchFile().getFilename());

      getSelectedSketchFile().disable();

      int selectedCodeIndex = getSelectedCodeIndex();

      tabs.remove(selectedCodeIndex);

      if (getCodeCount() <= 0) {

        tabs.clear();

        supportInvalidateOptionsMenu();
      }

      codePagerAdapter.notifyDataSetChanged();

      if (selectedCodeIndex == 0) {
        selectCode(0);
      } else if (selectedCodeIndex >= tabs.size() && tabs.size() > 0) {
        selectCode(tabs.size() - 1);
      }

      scheduleAutoCompile(true);

      message(getResources().getText(R.string.tab_delete_success));
    }
  }

  private void createInputDialog(String title, String message, String currentName, int key) {

    MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(this);

    alert.setTitle(title);
    alert.setMessage(message);

    EditText input = getGlobalState().createAlertDialogEditText(this, alert, currentName, true);

    alert.setPositiveButton(
        R.string.ok,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            String value = input.getText().toString();
            checkInputDialog(key, true, value);

            if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("use_hardware_keyboard", false)) {
              ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
          }
        });

    alert.setNegativeButton(
        R.string.cancel,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            checkInputDialog(key, false, "");

            if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("use_hardware_keyboard", false)) {
              ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
          }
        });

    AlertDialog dialog = alert.create();
    if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        .getBoolean("use_hardware_keyboard", false)) {
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    dialog.show();
  }

  private void checkInputDialog(int key, boolean completed, String value) {
    if (completed) {

      if (!(validateFileName(value)
          || (value.endsWith(".java")
              && value.length() > 5
              && validateFileName(value.substring(0, value.length() - 5))))) {
        return;
      }

      switch (key) {
        case RENAME_TAB:
          deleteLocalFile(getSelectedSketchFile().getFilename());

          getSelectedSketchFile().setTitle(value);
          codePagerAdapter.notifyDataSetChanged();

          scheduleAutoCompile(true);

          message(getResources().getText(R.string.tab_rename_success));

          break;
        case NEW_TAB:
          if (value.endsWith(".java")) {

            SketchFile meta = new SketchFile(value.substring(0, value.length() - 5));
            meta.setSuffix(".java");

            addTab(meta);
            selectCode(getCodeCount() - 1);

          } else {

            addTab(value);

            selectCode(getCodeCount() - 1);
          }

          if (getCodeCount() == 1) {
            supportInvalidateOptionsMenu();
          }

          message(getResources().getText(R.string.tab_new_success));

          break;
      }
    }
  }

  public void clearUndoRedoHistory() {
    for (SketchFile meta : tabs) {
      meta.clearUndoRedo();
    }
  }

  /**
   * @return whether or not we can write to the external storage
   */
  private boolean externalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) return true;
    else return false;
  }

  protected boolean validateFileName(String title) {

    if (title.length() <= 0) {
      error(getResources().getText(R.string.tab_name_invalid_no_char));
      return false;
    }

    char first = title.charAt(0);
    if ((first >= '0' && first <= '9') || first == '_') {
      error(getResources().getText(R.string.tab_name_invalid_first_char));
      return false;
    }

    for (int i = 0; i < title.length(); i++) {
      char c = title.charAt(i);
      if (c >= '0' && c <= '9') continue;
      if (c >= 'a' && c <= 'z') continue;
      if (c >= 'A' && c <= 'Z') continue;
      if (c == '_') continue;

      error(getResources().getText(R.string.tab_name_invalid_char));
      return false;
    }

    for (SketchFile meta : tabs) {
      if (meta.getTitle().equals(title)) {
        error(getResources().getText(R.string.tab_name_invalid_same_title));
        return false;
      }
    }

    return true;
  }

  private void launchTools() {
    ArrayList<Tool> toolList = getGlobalState().getToolsInList();

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle(R.string.editor_menu_tools);
    if (toolList.size() > 0) {

      builder.setItems(
          getGlobalState().listToolsInList(),
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
              runOnUiThread(toolList.get(which));
            }
          });
    } else {

      System.err.println(getResources().getString(R.string.tools_empty));
    }

    AlertDialog dialog = builder.create();

    dialog.setCanceledOnTouchOutside(true);
    dialog.show();
  }

  public void launchImportLibrary() {
    getGlobalState().rebuildLibraryList();
    String[] libList = getGlobalState().listLibraries();

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle(R.string.tool_import_library);
    if (libList.length > 0) {

      builder.setItems(
          libList,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
              addImports(
                  getGlobalState()
                      .getLibraryByName(libList[which])
                      .getPackageList(getGlobalState()));
            }
          });
    } else {

      TextView content = new TextView(this);
      content.setText(R.string.library_manager_no_contributed_libraries);
      content.setTextColor(getResources().getColor(R.color.grayed_out));
      content.setGravity(Gravity.CENTER);
      content.setLayoutParams(
          new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      content.setPadding(60, 60, 60, 60);
      content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

      builder.setView(content);
    }

    builder.setNeutralButton(R.string.library_manager_open, null);
    AlertDialog dialog = builder.create();

    dialog.setOnShowListener(
        dialog1 -> {
          Button b = ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_NEUTRAL);
          b.setOnClickListener(
              view -> {
                launchManageLibraries();

                dialog1.dismiss();
              });
        });

    dialog.setCanceledOnTouchOutside(true);
    dialog.show();
  }

  public void launchManageLibraries() {
    getGlobalState().rebuildLibraryList();

    Intent intent = new Intent(this, LibraryManagerActivity.class);
    startActivity(intent);
  }

  private void launchSketchProperties() {
    Intent intent = new Intent(this, SketchPropertiesActivity.class);
    startActivity(intent);
  }

  private void launchSettings() {
    startActivity(new Intent(this, SettingsActivity.class));
  }

  /**
   * Adds the imports of the given library to the top of the sketch, selecting the first tab if
   * necessary
   *
   * @param imports
   */
  public void addImports(String[] imports) {

    if (getGlobalState().isExample()) return;

    String importList = "";

    for (String im : imports) {

      importList += "import " + im + ".*;\n";
    }

    importList += "\n";

    if (getCodeCount() <= 0) return;

    selectCode(0);

    CodeEditText code = getSelectedCodeArea();

    code.setSelection(0);

    code.setUpdateText(importList + code.getText());

    code.updateTokens();
    code.updateBracketMatch();
  }

  /**
   * NOTE: This is not currently correctly implemented
   *
   * @return whether or not the sketch is saved
   */
  public boolean isSaved() {

    return saved;
  }

  /**
   * NOTE: This is not currently correctly implemented
   *
   * @param saved
   */
  public void setSaved(boolean saved) {
    this.saved = saved;
  }

  /**
   * @return an ArrayList of SketchFile tabs
   */
  public ArrayList<SketchFile> getSketchFiles() {
    return tabs;
  }

  /**
   * @return an array containing all of the tabs' SketchFile objects
   */
  public SketchFile[] getTabMetas() {
    SketchFile[] metas = new SketchFile[getCodeCount()];

    for (int i = 0; i < getCodeCount(); i++) {
      metas[i] = tabs.get(i);
    }

    return metas;
  }

  /**
   * Add a message to the console and automatically scroll to the bottom (if the user has this
   * feature turned on)
   *
   * @param msg
   */
  public void postConsole(String msg) {

    if (FLAG_SUSPEND_OUT_STREAM.get()
        && !PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pref_debug_global_verbose_output", false)) {
      return;
    }

    TextView tv = findViewById(R.id.console);

    tv.append(msg);

    ScrollView scroll = findViewById(R.id.console_scroller);
    HorizontalScrollView scrollX = findViewById(R.id.console_scroller_x);

    if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        .getBoolean("pref_scroll_lock", true))
      scroll.post(
          () -> {
            scroll.scrollTo(0, scroll.getHeight());

            scrollX.post(
                () -> {
                  scrollX.scrollTo(0, 0);
                });
          });
  }

  public class MessageTouchListener
      implements android.view.View.OnLongClickListener, android.view.View.OnTouchListener {
    private boolean pressed;
    private int touchOff;

    private View console;
    private View content;

    public MessageTouchListener() {
      super();

      pressed = false;

      console = findViewById(R.id.console_wrapper);
      content = findViewById(R.id.content);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
      view.performClick();

      if (keyboardVisible) return false;

      if (event.getAction() == MotionEvent.ACTION_DOWN) touchOff = (int) event.getY();

      if (pressed) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_MOVE:
            if (message == -1) {
              message = findViewById(R.id.buffer).getHeight();
            }

            FrameLayout autoCompileProgress = findViewById(R.id.auto_compile_progress_wrapper);

            int maxCode =
                content.getHeight()
                    - message
                    - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)
                    - tabBarContainer.getHeight()
                    - autoCompileProgress.getHeight();

            int y = (int) event.getY() - touchOff;

            int consoleDim = Math.max(Math.min(console.getHeight() - y, maxCode), 0);

            int codeDim = maxCode - consoleDim;

            codePager.setLayoutParams(
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, codeDim));
            console.setLayoutParams(
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, consoleDim));

            firstResize = false;

            if (consoleDim > 0) {
              consoleWasHidden = false;
            }

            return true;
          case MotionEvent.ACTION_UP:
            pressed = false;

            View buffer = findViewById(R.id.buffer);
            TextView messageArea = findViewById(R.id.message);

            switch (messageType) {
              case MESSAGE:
                buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back));
                messageArea.setTextColor(getResources().getColor(R.color.message_text));
                break;
              case ERROR:
                buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error));
                messageArea.setTextColor(getResources().getColor(R.color.error_text));
                break;
              case WARNING:
                buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_warning));
                messageArea.setTextColor(getResources().getColor(R.color.warning_text));
                break;
            }

            return true;
        }
      }

      return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onLongClick(View view) {

      if (keyboardVisible) return false;

      pressed = true;

      View buffer = findViewById(R.id.buffer);
      TextView messageArea = findViewById(R.id.message);

      switch (messageType) {
        case MESSAGE:
          buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_selected));
          messageArea.setTextColor(getResources().getColor(R.color.message_text));
          break;
        case ERROR:
          buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error_selected));
          messageArea.setTextColor(getResources().getColor(R.color.error_text));
          break;
        case WARNING:
          buffer.setBackgroundDrawable(
              getResources().getDrawable(R.drawable.back_warning_selected));
          messageArea.setTextColor(getResources().getColor(R.color.warning_text));
          break;
      }

      if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
          .getBoolean("pref_vibrate", true))
        ((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(200);

      return true;
    }
  }

  private class ConsoleStream extends OutputStream {
    byte single[] = new byte[1];
    private OutputStream pipeTo;
    private boolean enabled;

    public ConsoleStream(OutputStream pipeTo) {
      this.pipeTo = pipeTo;
      this.enabled = true;
    }

    public void disable() {
      enabled = false;
    }

    public void close() {}

    public void flush() {}

    public void write(byte b[]) {
      write(b, 0, b.length);
    }

    @Override
    public void write(byte b[], int offset, int length) {
      if (enabled) {

        try {
          pipeTo.write(b, offset, length);
        } catch (IOException e) {

        }

        String value = new String(b, offset, length);

        if (!(FLAG_SUSPEND_OUT_STREAM.get()
            && !PreferenceManager.getDefaultSharedPreferences(EditorActivity.this)
                .getBoolean("pref_debug_global_verbose_output", false))) {

          runOnUiThread(
              () -> {
                postConsole(value);
              });
        }
      }
    }

    @Override
    public void write(int b) {
      single[0] = (byte) b;
      write(single, 0, 1);
    }
  }

  public abstract class UpgradeChange implements Runnable {
    public int changeVersion;

    public UpgradeChange(int changeVersion) {
      this.changeVersion = changeVersion;
    }
  }

  public void runUpgradeChanges(int from, int to) {
    ArrayList<UpgradeChange> upgradeChanges = new ArrayList<UpgradeChange>();

    upgradeChanges.add(
        new UpgradeChange(13) {
          @Override
          public void run() {

            new Thread(
                    () ->
                        copyAssetFolder(
                            getAssets(),
                            "examples",
                            getGlobalState().getStarterExamplesFolder().getAbsolutePath()))
                .start();
          }
        });

    upgradeChanges.add(
        new UpgradeChange(14) {
          @Override
          public void run() {
            SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(EditorActivity.this);

            if (prefs.contains("pref_build_discard")) {
              SharedPreferences.Editor edit = prefs.edit();
              edit.putBoolean(
                  "pref_build_folder_keep", !prefs.getBoolean("pref_build_discard", true));
              edit.apply();
            }
          }
        });

    upgradeChanges.add(
        new UpgradeChange(16) {
          @Override
          public void run() {
            try {

              File examplesRepo = getGlobalState().getExamplesRepoFolder();
              if (examplesRepo.exists()) {
                APDE.deleteFile(examplesRepo);
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        });

    upgradeChanges.add(
        new UpgradeChange(22) {
          @Override
          public void run() {
            getGlobalState()
                .getTaskManager()
                .launchTask("recopyAndroidJarTask", false, null, false, new CopyAndroidJarTask());
          }
        });

    upgradeChanges.add(
        new UpgradeChange(22) {
          @Override
          public void run() {
            try {
              String oldSketchDataStr = readTempFile("sketchData.txt");
              String[] oldSketchData = oldSketchDataStr.split(";");
              if (oldSketchData.length >= 3) {
                StringBuilder newSketchData = new StringBuilder();
                for (int i = 0; i < 3; i++) {
                  newSketchData.append(oldSketchData[i]);
                  newSketchData.append(';');
                }
                newSketchData.append("0;");
                for (int i = 3; i < oldSketchData.length; i++) {
                  newSketchData.append(oldSketchData[i]);
                  newSketchData.append(';');
                }
                writeTempFile("sketchData.txt", newSketchData.toString());
              }
            } catch (Exception e) {
              e.printStackTrace();
              System.err.println(getResources().getString(R.string.apde_0_5_upgrade_error));
            }
          }
        });

    upgradeChanges.add(
        new UpgradeChange(30) {
          @Override
          public void run() {
            FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED = true;
          }
        });

    for (UpgradeChange upgradeChange : upgradeChanges) {
      if (from < upgradeChange.changeVersion && to >= upgradeChange.changeVersion) {
        upgradeChange.run();
      }
    }
  }
}
