package com.pierfrancescosoffritti.slidingdrawer_sample;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.pierfrancescosoffritti.slidingdrawer.SlidingDrawer;
import com.pierfrancescosoffritti.utils.FragmentsUtils;

public class MainActivity extends AppCompatActivity implements SlidingDrawerContainer, SlidingDrawer.OnSlideListener {

    SlidingDrawer slidingDrawer;

    FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setBackgroundDrawable(null);

        View target = findViewById(R.id.sample_view);
        slidingDrawer = (SlidingDrawer) findViewById(R.id.sliding_drawer);

        fab = (FloatingActionButton) findViewById(R.id.fab);

        target.setOnClickListener(new View.OnClickListener() {
            private boolean show = false;

            @Override
            public void onClick(View view) {
                Log.d(getClass().getSimpleName(), "click");

                if(!show)
                    getSupportActionBar().hide();
                else
                    getSupportActionBar().show();

                slidingDrawer.setState(slidingDrawer.getState() == SlidingDrawer.EXPANDED ? SlidingDrawer.COLLAPSED : SlidingDrawer.EXPANDED);

                show = !show;
            }
        });

        RootFragment fragment = RootFragment.newInstance(this);
        FragmentsUtils.swapFragments(getSupportFragmentManager(), R.id.root, fragment);

        slidingDrawer.addSlideListener(fragment);
        slidingDrawer.addSlideListener(this);
    }


    @Override
    public void setDragView(View view) {
        slidingDrawer.setDraggableView(view);
    }

    @Override
    public void onSlide(SlidingDrawer slidingDrawer, float currentSlide) {
        if(currentSlide == 0)
            fab.show();
        else
            fab.hide();

    }
}