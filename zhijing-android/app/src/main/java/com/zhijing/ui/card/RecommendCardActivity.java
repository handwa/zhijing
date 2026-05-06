package com.zhijing.ui.card;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zhijing.R;

import java.util.ArrayList;

public class RecommendCardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend_card);

        String title = getIntent().getStringExtra("title");
        String reason = getIntent().getStringExtra("reason");
        ArrayList<String> chain = getIntent().getStringArrayListExtra("chain");

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvReason = findViewById(R.id.tvReason);
        LinearLayout chainContainer = findViewById(R.id.chainContainer);
        Button btnExpand = findViewById(R.id.btnExpand);
        Button btnWhyBtn = findViewById(R.id.btnWhy);
        Button btnLater = findViewById(R.id.btnLater);

        tvTitle.setText(title);
        tvReason.setText(reason);

        btnWhyBtn.setOnClickListener(v -> {
            chainContainer.setVisibility(chainContainer.getVisibility() == android.view.View.VISIBLE
                    ? android.view.View.GONE : android.view.View.VISIBLE);
            if (chain != null) {
                chainContainer.removeAllViews();
                for (String step : chain) {
                    TextView stepView = new TextView(this);
                    stepView.setText(step);
                    stepView.setPadding(8, 4, 8, 4);
                    stepView.setTextSize(13);
                    chainContainer.addView(stepView);
                }
            }
        });

        btnExpand.setOnClickListener(v -> {
            // TODO: 跳转到知识整合摘要页
            finish();
        });

        btnLater.setOnClickListener(v -> finish());
    }
}
