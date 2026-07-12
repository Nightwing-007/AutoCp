package com.github.pushpavel.autocp.config.actions

import com.github.pushpavel.autocp.common.res.R
import com.github.pushpavel.autocp.config.AutoCpConfigType

class RunAutoCpAction :
    BaseRunAutoCpAction("Run with AutoCp", "Run the currently focused file with AutoCp", R.icons.logo13) {

    override val configurationFactory get() = AutoCpConfigType.factory
}
