package com.github.pushpavel.autocp.config.actions

import com.github.pushpavel.autocp.common.res.R
import com.github.pushpavel.autocp.config.stress.AutoCpStressConfigType

class RunAutoCpStressAction :
    BaseRunAutoCpAction("Stress Test with AutoCp", "Stress test currently focused file with AutoCp", R.icons.logo13) {

    override val configurationFactory get() = AutoCpStressConfigType.factory
}
