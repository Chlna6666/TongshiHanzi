/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.chlna.tongshihanzi.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main);
        NavHostFragment host=(NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if(host==null)throw new IllegalStateException("Navigation host missing");
        NavController controller=host.getNavController(); BottomNavigationView navigation=findViewById(R.id.bottom_navigation);
        NavigationUI.setupWithNavController(navigation,controller);
        controller.addOnDestinationChangedListener((navController,destination,arguments)->{int id=destination.getId();boolean root=id==R.id.searchFragment||id==R.id.favoritesFragment||id==R.id.settingsFragment;navigation.setVisibility(root?View.VISIBLE:View.GONE);});
    }
}
