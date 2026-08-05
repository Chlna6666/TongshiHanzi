/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui;

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
import com.chlna.tongshihanzi.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Hosts the application's navigation graph and owns system-bar inset handling. */
public final class MainActivity extends AppCompatActivity {
    private View navHost;
    private BottomNavigationView bottomNavigation;
    private int systemBottomInset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_main);

        navHost = findViewById(R.id.nav_host_fragment);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        applySystemBarAppearance();
        installInsets();

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) throw new IllegalStateException("Navigation host missing");

        NavController controller = host.getNavController();
        NavigationUI.setupWithNavController(bottomNavigation, controller);
        controller.addOnDestinationChangedListener((navController, destination, arguments) -> {
            int id = destination.getId();
            boolean root = id == R.id.searchFragment
                    || id == R.id.favoritesFragment
                    || id == R.id.settingsFragment;
            bottomNavigation.setVisibility(root ? View.VISIBLE : View.GONE);
            updateNavHostBottomPadding(root ? 0 : systemBottomInset);
        });
    }

    private void installInsets() {
        final int hostLeft = navHost.getPaddingLeft();
        final int hostTop = navHost.getPaddingTop();
        final int hostRight = navHost.getPaddingRight();
        final int hostBottom = navHost.getPaddingBottom();
        final int navLeft = bottomNavigation.getPaddingLeft();
        final int navTop = bottomNavigation.getPaddingTop();
        final int navRight = bottomNavigation.getPaddingRight();
        final int navBottom = bottomNavigation.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            systemBottomInset = bars.bottom;
            navHost.setPadding(
                    hostLeft + bars.left,
                    hostTop + bars.top,
                    hostRight + bars.right,
                    hostBottom + (bottomNavigation.getVisibility() == View.GONE ? bars.bottom : 0));
            bottomNavigation.setPadding(
                    navLeft + bars.left,
                    navTop,
                    navRight + bars.right,
                    navBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(findViewById(R.id.main_root));
    }

    private void updateNavHostBottomPadding(int bottom) {
        navHost.setPadding(
                navHost.getPaddingLeft(),
                navHost.getPaddingTop(),
                navHost.getPaddingRight(),
                bottom);
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