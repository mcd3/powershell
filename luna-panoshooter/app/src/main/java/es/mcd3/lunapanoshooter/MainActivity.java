package es.mcd3.lunapanoshooter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {

    private TextView serviceStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(245, 245, 245));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Luna PanoShooter");
        title.setTextSize(28f);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Captura automática de panoramas 360 de alta resolución");
        subtitle.setTextSize(17f);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, 0, 0, dp(24));
        root.addView(subtitle, matchWrap());

        serviceStatus = new TextView(this);
        serviceStatus.setTextSize(17f);
        serviceStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        serviceStatus.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(serviceStatus, matchWrap());

        TextView instructions = new TextView(this);
        instructions.setText(
                "1. Activa el servicio de accesibilidad.\n\n" +
                "2. Abre Insta360 y conecta la Luna.\n\n" +
                "3. Configura: Foto · UHD 4:3 · 1× con gran angular · JPG + RAW.\n\n" +
                "4. Coloca manualmente la cámara mirando al centro.\n\n" +
                "5. Muestra el control flotante y pulsa INICIAR dentro de Insta360."
        );
        instructions.setTextSize(17f);
        instructions.setTextColor(Color.rgb(35, 35, 35));
        instructions.setPadding(0, dp(18), 0, dp(18));
        root.addView(instructions, matchWrap());

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("ACTIVAR ACCESIBILIDAD");
        accessibilityButton.setTextSize(16f);
        accessibilityButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        root.addView(accessibilityButton, buttonParams());

        Button overlayButton = new Button(this);
        overlayButton.setText("MOSTRAR CONTROL FLOTANTE");
        overlayButton.setTextSize(16f);
        overlayButton.setOnClickListener(v -> {
            if (LunaAccessibilityService.isConnected()) {
                LunaAccessibilityService.showControlOverlay();
                serviceStatus.setText("Servicio activo · control flotante visible");
                serviceStatus.setTextColor(Color.rgb(0, 110, 45));
            } else {
                serviceStatus.setText("Activa primero el servicio de accesibilidad");
                serviceStatus.setTextColor(Color.rgb(180, 30, 30));
            }
        });
        root.addView(overlayButton, buttonParams());

        TextView sequence = new TextView(this);
        sequence.setText(
                "Secuencia validada: 31 fotografías\n" +
                "9 centrales · 9 superiores · 4 de cenit · 9 inferiores"
        );
        sequence.setTextSize(15f);
        sequence.setTextColor(Color.GRAY);
        sequence.setGravity(Gravity.CENTER_HORIZONTAL);
        sequence.setPadding(0, dp(22), 0, 0);
        root.addView(sequence, matchWrap());

        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serviceStatus == null) {
            return;
        }

        if (LunaAccessibilityService.isConnected()) {
            serviceStatus.setText("Servicio de accesibilidad activo");
            serviceStatus.setTextColor(Color.rgb(0, 110, 45));
        } else {
            serviceStatus.setText("Servicio de accesibilidad desactivado");
            serviceStatus.setTextColor(Color.rgb(180, 30, 30));
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
