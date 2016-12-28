package com.juns.wechat.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.juns.wechat.R;
import com.juns.wechat.bean.DynamicBean;
import com.juns.wechat.bean.UserBean;
import com.juns.wechat.manager.AccountManager;
import com.style.album.AlbumActivity;
import com.style.album.DynamicPublishImageAdapter;
import com.style.base.BaseRecyclerViewAdapter;
import com.style.base.BaseToolbarBtnActivity;
import com.style.constant.FileDirectory;
import com.style.constant.Skip;
import com.style.dialog.SelAvatarDialog;
import com.style.rxAndroid.callback.RXOtherCallBack;
import com.style.utils.BitmapUtil;
import com.style.utils.CommonUtil;
import com.style.utils.PictureUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;


public class AddNoteActivity extends BaseToolbarBtnActivity {
    private static final String TAG_ADD = "addTag";

    @Bind(R.id.et_content)
    EditText etContent;
    @Bind(R.id.recyclerView)
    RecyclerView recyclerView;

    private DynamicPublishImageAdapter adapter;
    private List<String> paths;
    protected boolean haveContent;
    private boolean haveImg;
    protected File photoFile;
    private SelAvatarDialog dialog;

    private UserBean curUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutResID = R.layout.activity_add_note;
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initData() {
        setToolbarTitle(R.string.note);
        getToolbarRightView().setText(R.string.complete);
        getToolbarRightView().setEnabled(false);

        curUser = AccountManager.getInstance().getUser();
        paths = null;
        paths = new ArrayList<>();
        paths.add(TAG_ADD);
        adapter = new DynamicPublishImageAdapter(AddNoteActivity.this, paths);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 4);
        gridLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener(new BaseRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, Object data) {
                showSelPicPopupWindow(position);
            }
        });

        adapter.setOnDeleteClickListener(new DynamicPublishImageAdapter.OnDeleteClickListener() {
            @Override
            public void onItemClickDelete(int position) {
                paths.remove(position);
                adapter.notifyDataSetChanged();
                setHaveDynamic();
            }
        });

        etContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
            }

            @Override
            public void afterTextChanged(Editable arg0) {
                if (arg0.length() > 0)
                    haveContent = true;
                else
                    haveContent = false;
                setHaveDynamic();
            }
        });

    }

    @Override
    protected void onClickTitleRightView() {
        addUserDynamic();
    }

    @Override
    protected void onBackFinish() {
        openDeletePromptDialog();
    }

    private void openDeletePromptDialog() {
        showGiveUpEditDialog(new OnGiveUpEditDialogListener() {
            @Override
            public void onPositiveButton() {
                finish();
            }

            @Override
            public void onNegativeButton() {

            }
        });
    }

    private void setHaveDynamic() {
        int number = adapter.getItemCount();
        if (number > 1)
            haveImg = true;
        else
            haveImg = false;
        if (haveContent || haveImg)
            getToolbarRightView().setEnabled(true);
        else
            getToolbarRightView().setEnabled(false);
    }

    private void addUserDynamic() {
        String content = etContent.getText().toString();
        final DynamicBean dynamicBean = new DynamicBean();
        dynamicBean.setContent(content);
        dynamicBean.setPublisherId(curUser.getUserId());
       /* HttpAction.addDynamic(content, null, new NetNormalCallBack() {
            @Override
            protected void onResultSuccess(Object data, String msg) {
                super.onResultSuccess(data, msg);
            }

            @Override
            protected void onFailure(int code, String msg) {
                super.onFailure(code, msg);
            }
        });*/
        showProgressDialog();
        runTask(new RXOtherCallBack() {
            @Override
            public Object doInBackground() {
                return dealPicture();
            }

            @Override
            public void OnSuccess(Object object) {
                dismissProgressDialog();
                File[] files = (File[]) object;
                logE(TAG, "文件个数==" + files.length);
            }

            @Override
            public void OnFailed(String message) {
                dismissProgressDialog();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case Skip.CODE_TAKE_ALBUM:
                    if (data != null) {
                        ArrayList<String> newPaths = data.getStringArrayListExtra("paths");
                        paths.clear();
                        paths.addAll(newPaths);
                        paths.add(TAG_ADD);
                        adapter.notifyDataSetChanged();
                    }
                    break;
                case Skip.CODE_TAKE_CAMERA:
                    if (photoFile.exists()) {
                        CommonUtil.notifyUpdateGallary(this, photoFile);// 通知系统更新相册
                        String filePath = photoFile.getAbsolutePath();// 获取相片的保存路径
                        int size = paths.size();
                        if (size >= 10) {
                            showToast("最多上传9张图片");
                        } else {
                            int location = 0;
                            if (size >= 1)
                                location = size - 1;
                            paths.add(location, filePath);
                            adapter.notifyDataSetChanged();
                        }
                    } else {
                        showToast(R.string.File_does_not_exist);
                    }
                    break;
            }
            setHaveDynamic();
        }
    }

    public File[] dealPicture() {
        int num = adapter.getItemCount() - 1;
        File[] files = null;
        if (num > 0) {
            try {
                files = new File[num];
                for (int i = 0; i < num; i++) {
                    String path = (String) adapter.getData(i);
                    if (path != null) {
                        int degree = PictureUtils.readPictureDegree(path);
                        logE("degree", degree + "");
                        Bitmap bitmap0 = BitmapUtil.revitionImageSize(path, 960, 540, 1280);
                        Bitmap bitmap = PictureUtils.rotaingBitmap(bitmap0, degree);
                        String name = String.valueOf(System.currentTimeMillis()) + ".imageCache";
                        BitmapUtil.saveBitmap(FileDirectory.DIR_CACHE, name, bitmap, 30, true);
                        String newPath = FileDirectory.DIR_CACHE + "/" + name;
                        File file = new File(newPath);
                        if (file.exists())
                            files[i] = file;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return files;
    }

    private void showSelPicPopupWindow(int position) {
        int count = adapter.getItemCount();
        if (position == count - 1) {
            if (dialog == null) {
                dialog = new SelAvatarDialog(AddNoteActivity.this);
                dialog.setOnItemClickListener(new SelAvatarDialog.OnItemClickListener() {
                    @Override
                    public void OnClickCamera() {
                        photoFile = CommonUtil.takePhoto(AddNoteActivity.this, FileDirectory.DIR_IMAGE, String.valueOf(System.currentTimeMillis()) + ".jpg");

                    }

                    @Override
                    public void OnClickPhoto() {
                        Intent intent = new Intent(AddNoteActivity.this, AlbumActivity.class);
                        int newCount = adapter.getItemCount();
                        ArrayList<String> cacheList = new ArrayList<>();
                        if (newCount > 1) {
                            for (int i = 0; i < newCount - 1; i++) {
                                cacheList.add(paths.get(i));
                            }
                        }
                        intent.putStringArrayListExtra("paths", cacheList);
                        intent.putExtra("maxNum", 9);
                        startActivityForResult(intent, Skip.CODE_TAKE_ALBUM);
                    }

                    @Override
                    public void OnClickCancel() {

                    }
                });
            }
            dialog.show();
        }
    }

}
