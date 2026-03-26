package com.example.averageprice;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends ComponentActivity {
    private PositionViewModel viewModel;

    private Spinner spinnerProfiles;
    private Button btnNewProfile;
    private Switch switchMode;
    private TextView tvModeLabel;
    private ImageButton btnDeleteProfile;

    private TextView tvProfit;
    private TextView tvProfitPercent;
    private TextView tvAveragePrice;
    private TextView tvTotalQuantity;
    private TextView tvTotalCost;

    private Button btnToggleOperations;
    private Button btnClearTransactions;
    private LinearLayout layoutOperations;

    private EditText etCurrentPrice;
    private EditText etPrice;
    private EditText etQuantity;
    private Button btnActionPrimary;
    private Button btnActionSecondary;

    private LinearLayout layoutEmpty;
    private LinearLayout layoutTransactions;

    private boolean isInputSectionExpanded = false;
    private boolean suppressProfileSelection = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(PositionViewModel.class);

        bindViews();
        bindEvents();
        refreshAllUi();
    }

    private void bindViews() {
        spinnerProfiles = findViewById(R.id.spinnerProfiles);
        btnNewProfile = findViewById(R.id.btnNewProfile);
        switchMode = findViewById(R.id.switchMode);
        tvModeLabel = findViewById(R.id.tvModeLabel);
        btnDeleteProfile = findViewById(R.id.btnDeleteProfile);

        tvProfit = findViewById(R.id.tvProfit);
        tvProfitPercent = findViewById(R.id.tvProfitPercent);
        tvAveragePrice = findViewById(R.id.tvAveragePrice);
        tvTotalQuantity = findViewById(R.id.tvTotalQuantity);
        tvTotalCost = findViewById(R.id.tvTotalCost);

        btnToggleOperations = findViewById(R.id.btnToggleOperations);
        btnClearTransactions = findViewById(R.id.btnClearTransactions);
        layoutOperations = findViewById(R.id.layoutOperations);

        etCurrentPrice = findViewById(R.id.etCurrentPrice);
        etPrice = findViewById(R.id.etPrice);
        etQuantity = findViewById(R.id.etQuantity);
        btnActionPrimary = findViewById(R.id.btnActionPrimary);
        btnActionSecondary = findViewById(R.id.btnActionSecondary);

        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutTransactions = findViewById(R.id.layoutTransactions);
    }

    private void bindEvents() {
        spinnerProfiles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (suppressProfileSelection) {
                    return;
                }
                Object selected = parent.getItemAtPosition(position);
                if (selected != null) {
                    viewModel.switchProfile(selected.toString());
                    refreshAllUi();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        btnNewProfile.setOnClickListener(v -> showCreateProfileDialog());

        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.toggleShortMode(isChecked);
            refreshAllUi();
        });

        btnDeleteProfile.setOnClickListener(v -> showDeleteProfileConfirmDialog());

        btnToggleOperations.setOnClickListener(v -> {
            isInputSectionExpanded = !isInputSectionExpanded;
            refreshOperationPanelState();
        });

        btnClearTransactions.setOnClickListener(v -> showClearTransactionsDialog());

        etCurrentPrice.addTextChangedListener(simpleWatcher(s -> {
            viewModel.setCurrentPrice(s);
            refreshProfitSection();
        }));

        btnActionPrimary.setOnClickListener(v -> submitAction(true));
        btnActionSecondary.setOnClickListener(v -> submitAction(false));
    }

    private TextWatcher simpleWatcher(OnTextChanged callback) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                callback.onChanged(s == null ? "" : s.toString());
            }
        };
    }

    private void refreshAllUi() {
        refreshProfileSpinner();

        tvModeLabel.setText(viewModel.isShortMode() ? "做空" : "做多");
        if (switchMode.isChecked() != viewModel.isShortMode()) {
            switchMode.setChecked(viewModel.isShortMode());
        }

        if (!etCurrentPrice.getText().toString().equals(viewModel.getCurrentPrice())) {
            etCurrentPrice.setText(viewModel.getCurrentPrice());
            etCurrentPrice.setSelection(etCurrentPrice.getText().length());
        }

        refreshProfitSection();

        tvAveragePrice.setText(format4(viewModel.getAveragePrice()));
        tvTotalQuantity.setText(format2(viewModel.getTotalQuantity()));
        tvTotalCost.setText(format2(viewModel.getTotalCost()));

        updateActionButtons();
        refreshOperationPanelState();
        refreshTransactionList();
    }

    private void refreshProfileSpinner() {
        List<String> names = viewModel.getProfileNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        suppressProfileSelection = true;
        spinnerProfiles.setAdapter(adapter);

        int index = names.indexOf(viewModel.getCurrentProfileName());
        if (index >= 0) {
            spinnerProfiles.setSelection(index, false);
        }
        suppressProfileSelection = false;
    }

    private void refreshOperationPanelState() {
        layoutOperations.setVisibility(isInputSectionExpanded ? View.VISIBLE : View.GONE);
        btnToggleOperations.setText(isInputSectionExpanded ? "收起操作" : "展开操作");
    }

    private void refreshProfitSection() {
        double profit = viewModel.getCurrentProfit();
        double profitPercent = viewModel.getCurrentProfitPercent();
        int color = profit >= 0 ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828");

        String sign = profit >= 0 ? "+" : "";
        tvProfit.setText(sign + format2(profit));
        tvProfitPercent.setText(format2(profitPercent) + "%");

        tvProfit.setTextColor(color);
        tvProfitPercent.setTextColor(color);
    }

    private void updateActionButtons() {
        boolean isShort = viewModel.isShortMode();

        btnActionPrimary.setText(isShort ? "买入平仓" : "加仓");
        btnActionSecondary.setText(isShort ? "卖出开仓" : "减仓");

        btnActionPrimary.setBackgroundColor(isShort ? Color.parseColor("#C62828") : Color.parseColor("#2E7D32"));
        btnActionSecondary.setBackgroundColor(isShort ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        btnActionPrimary.setTextColor(Color.WHITE);
        btnActionSecondary.setTextColor(Color.WHITE);
    }

    private void submitAction(boolean primary) {
        Double price = parseInput(etPrice.getText().toString());
        Double quantity = parseInput(etQuantity.getText().toString());

        if (price == null || quantity == null) {
            Toast.makeText(this, "请输入有效的单价和数量", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isShort = viewModel.isShortMode();
        TransactionType type;
        if (primary) {
            type = isShort ? TransactionType.BUY : TransactionType.BUY;
        } else {
            type = isShort ? TransactionType.SELL : TransactionType.SELL;
        }

        boolean success = viewModel.addTransaction(price, quantity, type);
        if (!success) {
            Toast.makeText(this, "减仓/平仓数量不能超过当前持仓", Toast.LENGTH_SHORT).show();
            return;
        }

        etPrice.setText("");
        etQuantity.setText("");
        refreshAllUi();
    }

    private void refreshTransactionList() {
        layoutTransactions.removeAllViews();
        List<Transaction> txs = new ArrayList<>(viewModel.getTransactions());
        Collections.reverse(txs);

        if (txs.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }

        layoutEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Transaction tx : txs) {
            View item = inflater.inflate(R.layout.item_transaction, layoutTransactions, false);

            View accent = item.findViewById(R.id.viewAccent);
            TextView tvLabel = item.findViewById(R.id.tvTxLabel);
            TextView tvDetail = item.findViewById(R.id.tvTxDetail);
            ImageButton btnDelete = item.findViewById(R.id.btnDeleteTx);

            boolean isBuy = tx.getType() == TransactionType.BUY;
            boolean isOpening = viewModel.isShortMode() ? !isBuy : isBuy;
            int accentColor = isOpening ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828");
            accent.setBackgroundColor(accentColor);

            String label;
            if (viewModel.isShortMode() && isBuy) {
                label = "买入平仓";
            } else if (viewModel.isShortMode()) {
                label = "卖出开仓";
            } else if (isBuy) {
                label = "买入加仓";
            } else {
                label = "卖出减仓";
            }

            tvLabel.setText(label);
            tvLabel.setTextColor(accentColor);
            tvDetail.setText("单价: " + trimDouble(tx.getPrice()) + "  |  数量: " + trimDouble(tx.getQuantity()));

            btnDelete.setOnClickListener(v -> showDeleteTransactionDialog(tx));
            item.setOnLongClickListener(v -> {
                showEditTransactionDialog(tx);
                return true;
            });

            layoutTransactions.addView(item);
        }
    }

    private void showCreateProfileDialog() {
        final EditText input = new EditText(this);
        input.setHint("仓位名称");

        new AlertDialog.Builder(this)
                .setTitle("新建档案")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString();
                    if (!viewModel.createNewProfile(name)) {
                        Toast.makeText(this, "仓位名称为空或已存在", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    refreshAllUi();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteProfileConfirmDialog() {
        String profileName = viewModel.getCurrentProfileName();
        new AlertDialog.Builder(this)
                .setTitle("删除确认")
                .setMessage("确定要删除仓位 \"" + profileName + "\" 及其所有数据吗？")
                .setPositiveButton("确认删除", (dialog, which) -> {
                    boolean success = viewModel.deleteCurrentProfile();
                    if (!success) {
                        Toast.makeText(this, "至少保留一个仓位", Toast.LENGTH_SHORT).show();
                    }
                    refreshAllUi();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showClearTransactionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清空确认")
                .setMessage("确定要清空所有交易记录吗？此操作不可恢复。")
                .setPositiveButton("确认清空", (dialog, which) -> {
                    viewModel.clearAllTransactions();
                    refreshAllUi();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteTransactionDialog(Transaction tx) {
        new AlertDialog.Builder(this)
                .setTitle("删除确认")
                .setMessage("确定要删除这条交易记录吗？")
                .setPositiveButton("确认删除", (dialog, which) -> {
                    viewModel.removeTransaction(tx);
                    refreshAllUi();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditTransactionDialog(Transaction tx) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_transaction, null, false);
        EditText etEditPrice = dialogView.findViewById(R.id.etEditPrice);
        EditText etEditQuantity = dialogView.findViewById(R.id.etEditQuantity);

        etEditPrice.setText(trimDouble(tx.getPrice()));
        etEditQuantity.setText(trimDouble(tx.getQuantity()));

        new AlertDialog.Builder(this)
                .setTitle("编辑交易记录")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    Double price = parseInput(etEditPrice.getText().toString());
                    Double quantity = parseInput(etEditQuantity.getText().toString());
                    if (price == null || quantity == null) {
                        Toast.makeText(this, "请输入有效的单价和数量", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean success = viewModel.updateTransaction(tx, price, quantity);
                    if (!success) {
                        Toast.makeText(this, "减仓/平仓数量不能超过当前持仓", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    refreshAllUi();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String format2(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private String format4(double value) {
        return String.format(Locale.getDefault(), "%.4f", value);
    }

    private String trimDouble(double value) {
        if (value == (long) value) {
            return String.format(Locale.getDefault(), "%d", (long) value);
        }
        return String.format(Locale.getDefault(), "%s", value);
    }

    private Double parseInput(String text) {
        try {
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private interface OnTextChanged {
        void onChanged(String text);
    }
}
