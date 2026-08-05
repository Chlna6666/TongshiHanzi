/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.chlna6666.tongshihanzi.R;
import com.chlna6666.tongshihanzi.util.MotionEffects;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Hosts navigation, system-bar insets and the interruptible bottom-bar transition. */
public final class MainActivity extends AppCompatActivity {
    private View navHost;
    private BottomNavigationView bottomNavigation;
    private int systemBottomInset;
    private int bottomNavigationHeight;
    private boolean rootDestination = true;

    private int hostLeft;
    private int hostTop;
    private int hostRight;
    private int hostBottom;
    private int navLeft;
    private int navTop;
    private int navRight;
    private int navBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_main);

        navHost = findViewById(R.id.nav_host_fragment);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        captureBasePadding();
        applySystemBarAppearance();
        installInsets();
        installBottomNavigationMotion();

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) {
            throw new IllegalStateException("Navigation host missing");
        }

        NavController controller = host.getNavController();
        NavigationUI.setupWithNavController(bottomNavigation, controller);
        controller.addOnDestinationChangedListener((navController, destination, arguments) -> {
            int id = destination.getId();
            rootDestination = id == R.id.searchFragment
                    || id == R.id.favoritesFragment
                    || id == R.id.settingsFragment;
            updateNavHostBottomPadding();
            MotionEffects.setBottomBarVisible(
                    bottomNavigation,
                    rootDestination,
                    bottomNavigationHeight);
        });
    }

    private void captureBasePadding() {
        hostLeft = navHost.getPaddingLeft();
        hostTop = navHost.getPaddingTop();
        hostRight = navHost.getPaddingRight();
        hostBottom = navHost.getPaddingBottom();
        navLeft = bottomNavigation.getPaddingLeft();
        navTop = bottomNavigation.getPaddingTop();
        navRight = bottomNavigation.getPaddingRight();
        navBottom = bottomNavigation.getPaddingBottom();
    }

    private void installInsets() {
        View root = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            systemBottomInset = bars.bottom;
            navHost.setPadding(
                    hostLeft + bars.left,
                    hostTop + bars.top,
                    hostRight + bars.right,
                    navHost.getPaddingBottom());
            bottomNavigation.setPadding(
                    navLeft + bars.left,
                    navTop,
                    navRight + bars.right,
                    navBottom + bars.bottom);
            bottomNavigation.post(() -> {
                bottomNavigationHeight = bottomNavigation.getHeight();
                updateNavHostBottomPadding();
            });
            updateNavHostBottomPadding();
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void installBottomNavigationMotion() {
        bottomNavigation.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                    oldLeft, oldTop, oldRight, oldBottom) -> {
            int measured = bottom - top;
            if (measured != bottomNavigationHeight) {
                bottomNavigationHeight = measured;
                updateNavHostBottomPadding();
            }
        });
        bottomNavigation.post(() -> {
            for (int index = 0; index < bottomNavigation.getMenu().size(); index++) {
                int itemId = bottomNavigation.getMenu().getItem(index).getItemId();
                View item = bottomNavigation.findViewById(itemId);
                if (item != null) {
                    MotionEffects.installPressFeedback(item);
                }
            }
        });
    }

    private void updateNavHostBottomPadding() {
        if (navHost == null) {
            return;
        }
        int bottom = rootDestination
                ? Math.max(bottomNavigationHeight, systemBottomInset)
                : systemBottomInset;
        navHost.setPadding(
                navHost.getPaddingLeft(),
                navHost.getPaddingTop(),
                navHost.getPaddingRight(),
                hostBottom + bottom);
    }

    private void applySystemBarAppearance() {
        boolean darkTheme = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!darkTheme);
        controller.setAppearanceLightNavigationBars(!darkTheme);
    }
}
