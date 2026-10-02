<template>
  <view class="container">
    <!--input列表 -->
    <view class="input-box">
      <view class="section">
        <text>收 货 人</text>
        <input
          placeholder="姓名"
          type="text"
          maxlength="15"
          :value="receiver"
          @input="onReceiverInput"
        >
      </view>
      <view class="section">
        <text>手机号码</text>
        <input
          placeholder="11位手机号码"
          type="number"
          maxlength="11"
          :value="mobile"
          @input="onMobileInput"
        >
      </view>
      <view
        class="section"
        @tap="translate"
      >
        <text>所在地区</text>
        <view class="pca">
          {{ province }} {{ city }} {{ area }}
        </view>
        <view
          class="animation-element-wrapper"
          :class="{ show: show }"
          @tap.stop="hiddenFloatView"
        >
          <view
            class="animation-element"
            @tap.stop="nono"
          >
            <text
              class="right-bt"
              @tap.stop="hiddenFloatView"
            >
              确定
            </text>
            <view class="line" />
            <picker-view
              class="region-picker"
              indicator-style="height: 68rpx;"
              :value="valArr"
              @change="bindChange"
              @tap.stop="nono"
            >
              <!--省-->
              <picker-view-column class="region-picker-column region-picker-column--province">
                <view
                  v-for="(item, indexs) in provArray"
                  :key="indexs"
                  class="region-picker-item"
                >
                  <text class="region-picker-text">
                    {{ item.areaName }}
                  </text>
                </view>
              </picker-view-column>
              <!--地级市-->
              <picker-view-column class="region-picker-column region-picker-column--city">
                <view
                  v-for="(item, indexss) in cityArray"
                  :key="indexss"
                  class="region-picker-item"
                >
                  <text class="region-picker-text">
                    {{ item.areaName }}
                  </text>
                </view>
              </picker-view-column>
              <!--区县-->
              <picker-view-column class="region-picker-column region-picker-column--area">
                <view
                  v-for="(item, indexsss) in areaArray"
                  :key="indexsss"
                  class="region-picker-item"
                >
                  <text class="region-picker-text">
                    {{ item.areaName }}
                  </text>
                </view>
              </picker-view-column>
            </picker-view>
          </view>
        </view>

        <view class="arrow">
          <image src="@/static/images/icon/more.png" />
        </view>
      </view>
      <view class="section">
        <text>详细地址</text>
        <input
          placeholder="如楼号/单元/门牌号"
          type="text"
          :value="addr"
          @input="onAddrInput"
        >
      </view>
    </view>
    <!-- end input列表 -->
    <!-- 功能按钮 -->
    <view class="btn-box">
      <view
        class="keep btn"
        @tap="onSaveAddr"
      >
        <text>保存收货地址</text>
      </view>

      <view
        v-if="addrId!=0"
        class="clear btn"
        @tap="onDeleteAddr"
      >
        <text>删除收货地址</text>
      </view>
    </view>
    <!-- end 功能按钮 -->
  </view>
</template>

<script setup>
const addrId = ref(0)
const city = ref('')
const area = ref('')
const provinceId = ref(0)
const cityId = ref(0)
const areaId = ref(0)
const receiver = ref('')
const mobile = ref('')
const addr = ref('')
const province = ref('')
onLoad((options) => {
  if (options.addrId) {
    uni.showLoading()

    http.request({
      url: '/p/address/addrInfo/' + options.addrId,
      method: 'GET'
    })
      .then(({ data }) => {
        province.value = data.province
        city.value = data.city
        area.value = data.area
        provinceId.value = data.provinceId
        cityId.value = data.cityId
        areaId.value = data.areaId
        receiver.value = data.receiver
        mobile.value = data.mobile
        addr.value = data.addr
        addrId.value = options.addrId
        initCityData(data.provinceId, data.cityId, data.areaId)
        uni.hideLoading()
      })
  } else {
    initCityData(provinceId.value, cityId.value, areaId.value)
  }
})

const provArray = ref([])
const valArr = ref([0, 0, 0])
const initCityData = (provinceIdParam, cityIdParam, areaIdParam) => {
  uni.showLoading()
  http.request({
    url: '/p/area/listByPid',
    method: 'GET',
    data: {
      pid: 0
    }
  })
    .then(({ data }) => {
      provArray.value = data
      // 有默认省份时定位到对应列
      if (provinceIdParam) {
        for (const index in data) {
          // 找到当前省份后同步 picker 索引
          if (data[index].areaId === provinceIdParam) {
            valArr.value = [parseInt(index), valArr.value[1], valArr.value[2]]
          }
        }
      }
      getCityArray(provinceIdParam || data[0].areaId, cityIdParam, areaIdParam)
      uni.hideLoading()
    })
}

let indexArr = [0, 0, 0]
const areaArray = ref([])
const cityArray = ref([])

/**
 * 同步当前选中的省市区
 */
const syncSelectedArea = () => {
  const provinceItem = provArray.value[valArr.value[0]]
  const cityItem = cityArray.value[valArr.value[1]]
  const areaItem = areaArray.value[valArr.value[2]]

  // 数据未加载完成时先跳过同步
  if (!provinceItem || !cityItem || !areaItem) {
    return
  }

  province.value = provinceItem.areaName
  city.value = cityItem.areaName
  area.value = areaItem.areaName
  provinceId.value = provinceItem.areaId
  cityId.value = cityItem.areaId
  areaId.value = areaItem.areaId
}

/**
 * 滑动事件
 */
const bindChange = (e) => {
  // 判断滑动的是第几个column
  const val = [...e.detail.value]
  // 若省份column做了滑动则定位到地级市和区县第一位
  if (indexArr[0] !== val[0]) {
    val[1] = 0
    val[2] = 0 // 更新数据
    valArr.value = val
    indexArr = val
    // 省份变化后重新获取地级市数据
    getCityArray(provArray.value[val[0]].areaId)
    return
  }

  // 若省份column未做滑动，地级市做了滑动则定位区县第一位
  if (indexArr[1] !== val[1]) {
    val[2] = 0 // 更新数据
    valArr.value = val
    indexArr = val
    getAreaArray(cityArray.value[val[1]].areaId) // 获取区县数据
    return
  }

  indexArr = val
  valArr.value = val
  syncSelectedArea()
}

const show = ref(false)
/**
 * 移动按钮点击事件
 */
const translate = () => {
  show.value = true
}

/**
 * 隐藏弹窗浮层
 */
const hiddenFloatView = () => {
  show.value = false
}

/**
 * 阻止弹层内部点击冒泡
 */
const nono = () => {}

/**
 * 根据省份ID获取 城市数据
 */
const getCityArray = (provinceIdParam, cityIdParam, areaIdParam) => {
  http.request({
    url: '/p/area/listByPid',
    method: 'GET',
    data: {
      pid: provinceIdParam
    }
  })
    .then(({ data }) => {
      cityArray.value = data
      // 有默认城市时定位到对应列
      if (cityIdParam) {
        for (const index in data) {
          // 找到当前城市后同步 picker 索引
          if (data[index].areaId === cityIdParam) {
            valArr.value = [valArr.value[0], parseInt(index), valArr.value[2]]
          }
        }
      } else {
        // 切换省份时默认选中第一个城市
        valArr.value = [valArr.value[0], 0, valArr.value[2]]
      }
      getAreaArray(cityIdParam || data[0].areaId, areaIdParam)
      uni.hideLoading()
    })
}

/**
 * 根据城市ID获取 区数据
 */
const getAreaArray = (cityIdParam, areaIdParam) => {
  http.request({
    url: '/p/area/listByPid',
    method: 'GET',
    data: {
      pid: cityIdParam
    }
  }).then(({ data }) => {
    areaArray.value = data
    // 有默认区县时定位到对应列
    if (areaIdParam) {
      for (const _index in data) {
        // 找到当前区县后同步 picker 索引
        if (data[_index].areaId === areaIdParam) {
          valArr.value = [valArr.value[0], valArr.value[1], parseInt(_index)]
        }
      }
    } else {
      // 切换城市时默认选中第一个区县
      valArr.value = [valArr.value[0], valArr.value[1], 0]
    }
    indexArr = [...valArr.value]
    syncSelectedArea()
    uni.hideLoading()
  })
}

/**
 * 保存地址
 */
const onSaveAddr = () => {
  const receiverParam = receiver.value.trim()
  const mobileParam = mobile.value.trim()
  const addrParam = addr.value.trim()

  if (!receiverParam) {
    receiver.value = ''
    uni.showToast({
      title: '请输入收货人姓名',
      icon: 'none'
    })
    return
  }

  if (!mobileParam) {
    uni.showToast({
      title: '请输入手机号码',
      icon: 'none'
    })
    return
  }

  if (mobileParam.length != 11) {
    uni.showToast({
      title: '请输入正确的手机号码',
      icon: 'none'
    })
    return
  }

  if (!addrParam) {
    addr.value = ''
    uni.showToast({
      title: '请输入详细地址',
      icon: 'none'
    })
    return
  }

  uni.showLoading()
  let url = '/p/address/addAddr'
  let method = 'POST'

  if (addrId.value != 0) {
    url = '/p/address/updateAddr'
    method = 'PUT'
  } // 添加或修改地址

  http.request({
    url,
    method,
    data: {
      receiver: receiverParam,
      mobile: mobileParam,
      addr: addrParam,
      province: province.value,
      provinceId: provinceId.value,
      city: city.value,
      cityId: cityId.value,
      areaId: areaId.value,
      area: area.value,
      userType: 0,
      addrId: addrId.value
    }
  })
    .then(() => {
      uni.hideLoading()
      uni.navigateBack({
        delta: 1
      })
    })
}
const onReceiverInput = (e) => {
  receiver.value = e.detail.value
}
const onMobileInput = (e) => {
  mobile.value = e.detail.value
}
const onAddrInput = (e) => {
  addr.value = e.detail.value
}

/**
 * 删除配送地址
 */
const onDeleteAddr = () => {
  uni.showModal({
    title: '',
    content: '确定要删除此收货地址吗？',
    confirmColor: '#eb2444',

    success (res) {
      if (res.confirm) {
        const addrIdParam = addrId.value
        uni.showLoading()
        http.request({
          url: '/p/address/deleteAddr/' + addrIdParam,
          method: 'DELETE'
        })
          .then(() => {
            uni.hideLoading()
            uni.navigateBack({
              delta: 1
            })
          })
      }
    }
  })
}
</script>

<style scoped lang="scss">
@use './editAddress.scss';
</style>
