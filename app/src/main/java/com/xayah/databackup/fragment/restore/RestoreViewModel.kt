package com.xayah.databackup.fragment.restore

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.airbnb.lottie.LottieAnimationView
import com.drakeet.multitype.MultiTypeAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.topjohnwu.superuser.Shell
import com.xayah.databackup.App
import com.xayah.databackup.MainActivity
import com.xayah.databackup.R
import com.xayah.databackup.adapter.AppListAdapter
import com.xayah.databackup.data.AppEntity
import com.xayah.databackup.databinding.FragmentRestoreBinding
import com.xayah.databackup.databinding.LayoutProcessingBinding
import com.xayah.databackup.util.Command
import com.xayah.databackup.util.readBackupSavePath
import com.xayah.databackup.util.readIsCustomDirectoryPath
import com.xayah.design.view.fastInitialize
import com.xayah.design.view.notifyDataSetChanged
import com.xayah.design.view.setWithResult
import com.xayah.materialyoufileexplorer.MaterialYouFileExplorer
import kotlinx.coroutines.*
import java.text.Collator
import java.util.*

class RestoreViewModel : ViewModel() {
    var binding: FragmentRestoreBinding? = null

    var isProcessing: Boolean = false

    var appList: MutableList<AppEntity> = mutableListOf()

    var appListAll: MutableList<AppEntity> = mutableListOf()

    var isFiltering = false

    lateinit var mAdapter: MultiTypeAdapter

    var backupPath: String? = null

    var time: Long = 0
    var index = 0
    var total = 0

    var success = 0
    var failed = 0

    var currentAppName = MutableLiveData<String?>()
    var currentAppIcon = MutableLiveData<Drawable?>()

    var selectAllApp = false
    var selectAllData = false
    var selectAll = false

    fun initialize(
        context: Context,
        materialYouFileExplorer: MaterialYouFileExplorer,
        onInitialized: () -> Unit
    ) {
        val mContext = context as FragmentActivity

        // 设置底部栏菜单按钮
        binding?.bottomAppBar?.menu?.let { setSearchView(mContext, it) }
        binding?.bottomAppBar?.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.restore_reverse -> {
                    setReverse(mContext)
                }
                R.id.restore_add -> {
                    binding?.linearLayout?.visibility = View.GONE
                    materialYouFileExplorer.toExplorer(
                        context, false, "default", arrayListOf(), true
                    ) { path, _ ->
                        backupPath = path
                        initialize(context, materialYouFileExplorer) {}
                    }
                }
            }
            true
        }

        // 确定事件
        binding?.floatingActionButton?.setOnClickListener {
            setConfirm(mContext) {
                binding?.coordinatorLayout?.removeView(binding?.bottomAppBar)
                binding?.coordinatorLayout?.removeView(binding?.floatingActionButton)
            }
        }

        // 加载进度
        val linearProgressIndicator = LinearProgressIndicator(mContext).apply { fastInitialize() }
        binding?.relativeLayout?.addView(linearProgressIndicator)
        if (!isProcessing) {
            // 没有Processing
            mAdapter = MultiTypeAdapter().apply {
                register(AppListAdapter(null, context))
                CoroutineScope(Dispatchers.IO).launch {
                    // 按照字母表排序
                    if (backupPath == null) backupPath = context.readBackupSavePath()
                    backupPath?.let {
                        val mAppList = Command.getAppList(context, it).apply {
                            sortWith { appEntity1, appEntity2 ->
                                val collator = Collator.getInstance(Locale.CHINA)
                                collator.getCollationKey((appEntity1 as AppEntity).appName)
                                    .compareTo(collator.getCollationKey((appEntity2 as AppEntity).appName))
                            }
                        }
                        appList = mAppList
                        appListAll = mAppList
                        items = appList
                        withContext(Dispatchers.Main) {
                            binding?.recyclerView?.notifyDataSetChanged()
                            if (appList.isEmpty()) {
                                binding?.linearLayout?.visibility = View.VISIBLE
                            } else {
                                binding?.linearLayout?.visibility = View.GONE
                            }
                            linearProgressIndicator.visibility = View.GONE
                            binding?.recyclerView?.visibility = View.VISIBLE
                            onInitialized()
                        }
                    }
                }
            }
        } else {
            // 恢复状态
            // 设置Processing布局
            onProcessing(mContext)
            onInitialized()
        }

        binding?.recyclerView?.apply {
            adapter = mAdapter
            fastInitialize()
        }
    }

    private fun setSearchView(context: Context, menu: Menu) {
        val searchView = SearchView(context).apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let {
                        appList =
                            appListAll.filter {
                                it.appName.contains(newText.lowercase()) or it.appName.contains(
                                    newText.uppercase()
                                )
                            }
                                .toMutableList()
                        mAdapter.items = appList
                        binding?.recyclerView?.notifyDataSetChanged()
                    }
                    return false
                }
            })
            queryHint = this.context.getString(R.string.please_type_key_word)
            isQueryRefinementEnabled = true
        }
        menu.findItem(R.id.restore_search).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW or MenuItem.SHOW_AS_ACTION_IF_ROOM)
            actionView = searchView
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
                    isFiltering = true
                    return true
                }

                override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
                    isFiltering = false
                    return true
                }
            })
        }
    }

    private fun setReverse(context: FragmentActivity) {
        PopupMenu(context, context.findViewById(R.id.restore_reverse)).apply {
            menuInflater.inflate(R.menu.select, menu)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.select_all -> {
                        for ((index, _) in appList.withIndex()) {
                            appList[index].backupApp = selectAll
                            appList[index].backupData = selectAll
                        }
                        binding?.recyclerView?.notifyDataSetChanged()
                        selectAll = !selectAll
                    }
                    R.id.select_all_app -> {
                        for ((index, _) in appList.withIndex()) {
                            appList[index].backupApp = selectAllApp
                        }
                        binding?.recyclerView?.notifyDataSetChanged()
                        selectAllApp = !selectAllApp
                    }
                    R.id.select_all_data -> {
                        for ((index, _) in appList.withIndex()) {
                            appList[index].backupData = selectAllData
                        }
                        binding?.recyclerView?.notifyDataSetChanged()
                        selectAllData = !selectAllData
                    }
                }
                true
            }
            show()
        }
    }

    private fun setConfirm(context: FragmentActivity, onCallback: () -> Unit) {
        if (isFiltering) {
            // 检查是否处于搜索模式
            Toast.makeText(
                context,
                context.getString(R.string.please_exit_search_mode),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(context.getString(R.string.tips))
                setCancelable(true)
                setMessage(context.getString(R.string.onConfirm))
                setNegativeButton(context.getString(R.string.cancel)) { _, _ -> }
                setPositiveButton(context.getString(R.string.confirm)) { _, _ ->
                    // 设置Processing布局
                    val layoutProcessingBinding = onProcessing(context)
                    // 清空日志
                    App.log.clear()
                    // 初始化数据
                    time = 0
                    success = 0
                    failed = 0
                    isProcessing = true
                    // 标题栏计数
                    CoroutineScope(Dispatchers.IO).launch {
                        while (isProcessing) {
                            delay(1000)
                            time += 1
                            val s = String.format("%02d", time % 60)
                            val m = String.format("%02d", time / 60 % 60)
                            val h = String.format("%02d", time / 3600 % 24)
                            withContext(Dispatchers.Main) {
                                (context as MainActivity).binding.toolbar.subtitle = "$h:$m:$s"
                                context.binding.toolbar.title =
                                    "${context.getString(R.string.restore_processing)}: ${index}/${total}"
                            }
                        }
                        withContext(Dispatchers.Main) {
                            (context as MainActivity).binding.toolbar.subtitle =
                                context.viewModel.versionName
                            context.binding.toolbar.title =
                                context.getString(R.string.restore_success)
                        }
                    }
                    onCallback()


                    // 重建appList
                    appList = mutableListOf()
                    appList.addAll(appListAll)
                    mAdapter.items = appList
                    for (i in appListAll) {
                        if (!i.backupApp && !i.backupData) {
                            appList.remove(i)
                        } else {
                            appList[appList.indexOf(i)].isProcessing = true
                        }
                    }

                    // 获取任务总个数
                    total = appList.size

                    CoroutineScope(Dispatchers.IO).launch {
                        for ((mIndex, i) in appList.withIndex()) {
                            // 推送数据
                            currentAppName.postValue(i.appName)
                            currentAppIcon.postValue(i.icon)
                            App.log.add("----------------------------")
                            App.log.add("${context.getString(R.string.restore_processing)}: ${i.packageName}")
                            var state = true // 该任务是否成功完成
                            index = mIndex

                            // 设置任务参数
                            val inPath = i.backupPath
                            val packageName = i.packageName

                            if (i.backupApp) {
                                // 选中恢复应用
                                App.log.add(context.getString(R.string.install_apk_processing))
                                Command.installAPK(inPath, packageName)
                            }
                            if (i.backupData) {
                                // 选中恢复数据
                                App.log.add(context.getString(R.string.restore_processing))
                                Command.restoreData(packageName, inPath).apply {
                                    if (!this)
                                        state = false
                                }
                            }
                            if (state)
                                success += 1
                            else
                                failed += 1
                        }
                        if (Command.ls("${backupPath}/media")) {
                            // 移除图标
                            withContext(Dispatchers.Main) {
                                layoutProcessingBinding.linearLayout.removeView(
                                    layoutProcessingBinding.shapeableImageViewAppIcon
                                )
                            }

                            // 恢复自定义目录
                            if (App.globalContext.readIsCustomDirectoryPath()) {
                                val mediaList = Shell.cmd("ls ${backupPath}/media").exec().out
                                for (i in mediaList) {
                                    // 推送数据
                                    currentAppName.postValue(i)

                                    // 恢复目录
                                    Command.decompressMedia("${backupPath}/media", i)
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            // 展示完成页面
                            binding?.relativeLayout?.removeAllViews()
                            val showResult = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.restore_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                BottomSheetDialog(context).apply {
                                    setWithResult(
                                        App.log.toString(),
                                        success,
                                        failed
                                    )
                                }
                            }
                            if (binding == null) {
                                showResult()
                            } else {
                                val lottieAnimationView = LottieAnimationView(context)
                                lottieAnimationView.apply {
                                    layoutParams =
                                        RelativeLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        ).apply {
                                            addRule(RelativeLayout.CENTER_IN_PARENT)
                                        }
                                    setAnimation(R.raw.success)
                                    playAnimation()
                                    addAnimatorUpdateListener { animation ->
                                        if (animation.animatedFraction == 1.0F) {
                                            showResult()
                                        }
                                    }
                                }
                                binding?.relativeLayout?.addView(lottieAnimationView)
                            }
                            isProcessing = false
                        }
                    }
                }
                show()
            }
        }
    }

    private fun onProcessing(context: FragmentActivity): LayoutProcessingBinding {
        // 移除底部栏和悬浮按钮
        binding?.coordinatorLayout?.removeView(binding?.bottomAppBar)
        binding?.coordinatorLayout?.removeView(binding?.floatingActionButton)
        // 载入Processing布局
        val layoutProcessingBinding =
            LayoutProcessingBinding.inflate(LayoutInflater.from(context)).apply {
                root.apply {
                    layoutParams =
                        RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                            .apply {
                                addRule(RelativeLayout.CENTER_IN_PARENT)
                            }
                }
            }
        binding?.relativeLayout?.removeAllViews()
        binding?.relativeLayout?.addView(layoutProcessingBinding.root)
        // 绑定可变数据
        currentAppName.observe(context) {
            it?.apply {
                layoutProcessingBinding.materialTextViewAppName.text = this
            }
        }
        currentAppIcon.observe(context) {
            it?.apply {
                layoutProcessingBinding.shapeableImageViewAppIcon.setImageDrawable(this)
            }
        }
        App.log.onObserveLast(context) {
            layoutProcessingBinding.materialTextViewLog.text = it
        }
        return layoutProcessingBinding
    }

}