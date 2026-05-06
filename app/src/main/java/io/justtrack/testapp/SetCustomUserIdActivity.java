package io.justtrack.testapp;

import android.view.View;
import android.widget.EditText;

import java.util.UUID;

import io.justtrack.testapp.databinding.ActivitySetCustomUserIdBinding;

public class SetCustomUserIdActivity extends BaseActivity<ActivitySetCustomUserIdBinding> {
    @Override
    protected ActivitySetCustomUserIdBinding getViewBinding() {
        return ActivitySetCustomUserIdBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initialize() {
        binding.randomCustomUserIdButton.setOnClickListener(this::onRandomCustomUserIdClick);
        binding.setCustomUserIdButton.setOnClickListener(this::onSetCustomUserIdClick);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (MainApplication.customUserId != null) {
            EditText customUserIdText = binding.customUserIdEditText;
            customUserIdText.setText(MainApplication.customUserId);
        }
    }

    private void onRandomCustomUserIdClick(View view) {
        assert view != null; // use it somehow

        EditText customUserIdText = binding.customUserIdEditText;
        customUserIdText.setText(UUID.randomUUID().toString());
    }

    private void onSetCustomUserIdClick(View view) {
        assert view != null; // use it somehow

        EditText customUserIdText = binding.customUserIdEditText;
        MainApplication.customUserId = customUserIdText.getText().toString();
        if (MainApplication.sdk != null) {
            MainApplication.sdk.setUserId(MainApplication.customUserId);
        }

        finish();
    }
}