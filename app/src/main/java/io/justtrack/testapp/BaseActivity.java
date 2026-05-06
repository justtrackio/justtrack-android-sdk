package io.justtrack.testapp;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.viewbinding.ViewBinding;

public abstract class BaseActivity<T extends ViewBinding> extends AppCompatActivity {
    protected T binding;

    protected abstract T getViewBinding();

    // child classes can implement this to setup event listeners etc.
    protected void initialize() {
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        binding = getViewBinding();
        setContentView(binding.getRoot());
        initialize();
    }
}
