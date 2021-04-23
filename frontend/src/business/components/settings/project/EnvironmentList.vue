<template>
  <div v-loading="result.loading">
    <el-card class="table-card">
      <!-- 表头 -->
      <template v-slot:header>
        <ms-table-header :title="$t('api_test.environment.environment_list')" :create-tip="btnTips"
                         :condition.sync="condition" :is-tester-permission="isTesterPermission" @search="search" @create="createEnv">
          <template v-slot:button>
            <ms-table-button :is-tester-permission="isTesterPermission" icon="el-icon-box"
                             :content="$t('commons.import')" @click="importJSON"/>
            <ms-table-button :is-tester-permission="isTesterPermission" icon="el-icon-box"
                             :content="$t('commons.export')" @click="exportJSON"/>
          </template>
        </ms-table-header>
      </template>
      <!-- 环境列表内容 -->
      <!-- 实现搜索,根据搜索内容变换显示的环境列表 -->
      <el-table border :data="environments.filter(env => !searchText || env.name.toLowerCase().includes(searchText.toLowerCase()))"
                @selection-change="handleSelectionChange" max-height="515" class="adjust-table" style="width: 100%" ref="table">
        <el-table-column type="selection"></el-table-column>
        <el-table-column :label="$t('commons.project')" width="250" show-overflow-tooltip>
          <template v-slot="scope">
            <span>{{idNameMap.get(scope.row.projectId)}}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('api_test.environment.name')" prop="name" show-overflow-tooltip>
        </el-table-column>
        <el-table-column :label="$t('api_test.environment.socket')" show-overflow-tooltip>
          <template v-slot="scope">
            <span v-if="parseDomainName(scope.row)!='SHOW_INFO'">{{ parseDomainName(scope.row) }}</span>
            <el-button size="mini" icon="el-icon-s-data" @click="showInfo(scope.row)" v-else>查看域名详情</el-button>
          </template>
        </el-table-column>
        <el-table-column :label="$t('commons.operating')">
          <template v-slot:default="scope">
            <ms-table-operator @editClick="editEnv(scope.row)" @deleteClick="deleteEnv(scope.row)">
              <template v-slot:middle>
                <ms-table-operator-button :tip="$t('commons.copy')" @exec="copyEnv(scope.row)" :is-tester-permission="isTesterPermission"
                                          icon="el-icon-document-copy" type="info"/>
              </template>
            </ms-table-operator>
          </template>
        </el-table-column>
      </el-table>
      <el-row type="flex" justify="end">
        <el-pagination layout="total" :total="total"></el-pagination>
      </el-row>
    </el-card>

    <!-- 创建、编辑、复制环境时的对话框 -->
    <el-dialog :visible.sync="dialogVisible" :close-on-click-modal="false" :title="dialogTitle" width="66%">
      <div class="project-item">
        <div>
          {{$t('project.select')}}
        </div>
        <el-select v-model="currentProjectId" filterable clearable>
          <el-option v-for="item in projectList" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
      </div>
      <environment-edit :environment="currentEnvironment" ref="environmentEdit" @close="close"
                        @refreshAfterSave="refresh">
      </environment-edit>
    </el-dialog>
    <environment-import :project-list="projectList" @refresh="refresh" ref="envImport"></environment-import>

    <el-dialog title="域名列表" :visible.sync="domainVisible">
      <el-table :data="conditions">
        <el-table-column prop="socket" :label="$t('load_test.domain')" show-overflow-tooltip width="180">
          <template v-slot:default="{row}">
            {{getUrl(row)}}
          </template>
        </el-table-column>
        <el-table-column prop="type" :label="$t('api_test.environment.condition_enable')" show-overflow-tooltip min-width="100px">
          <template v-slot:default="{row}">
            {{getName(row)}}
          </template>
        </el-table-column>
        <el-table-column prop="details" show-overflow-tooltip min-width="120px" :label="$t('api_test.value')">
          <template v-slot:default="{row}">
            {{getDetails(row)}}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" show-overflow-tooltip min-width="120px" :label="$t('commons.create_time')">
          <template v-slot:default="{row}">
            <span>{{ row.time | timestampFormatDate }}</span>
          </template>
        </el-table-column>
      </el-table>
      <span slot="footer" class="dialog-footer">
    <el-button @click="domainVisible = false" size="mini">{{$t('commons.cancel')}}</el-button>
    <el-button type="primary" @click="domainVisible = false" size="mini">{{$t('commons.confirm')}}</el-button>
  </span>
    </el-dialog>
  </div>
</template>

<script>
  import MsTableHeader from "@/business/components/common/components/MsTableHeader";
  import MsTableButton from "@/business/components/common/components/MsTableButton";
  import MsTableOperator from "@/business/components/common/components/MsTableOperator";
  import MsTableOperatorButton from "@/business/components/common/components/MsTableOperatorButton";
  import MsTablePagination from "@/business/components/common/pagination/TablePagination";
  import ApiEnvironmentConfig from "@/business/components/api/test/components/ApiEnvironmentConfig";
  import {Environment, parseEnvironment} from "@/business/components/api/test/model/EnvironmentModel";
  import EnvironmentEdit from "@/business/components/api/test/components/environment/EnvironmentEdit";
  import MsAsideItem from "@/business/components/common/components/MsAsideItem";
  import MsAsideContainer from "@/business/components/common/components/MsAsideContainer";
  import ProjectSwitch from "@/business/components/common/head/ProjectSwitch";
  import SearchList from "@/business/components/common/head/SearchList";
  import {checkoutTestManagerOrTestUser, downloadFile} from "@/common/js/utils";
  import EnvironmentImport from "@/business/components/settings/project/EnvironmentImport";

  export default {
    name: "EnvironmentList",
    components: {
      EnvironmentImport,
      SearchList,
      ProjectSwitch,
      MsAsideContainer,
      MsAsideItem,
      EnvironmentEdit,
      ApiEnvironmentConfig,
      MsTablePagination, MsTableOperatorButton, MsTableOperator, MsTableButton, MsTableHeader
    },
    data() {
      return {
        btnTips: this.$t('api_test.environment.create'),
        projectList: [],
        condition: {envName: ''},   //用于搜索框
        environments: [],
        currentEnvironment: new Environment(),
        result: {},
        dialogVisible: false,
        idNameMap: new Map(),
        dialogTitle: '',
        currentProjectId: '',   //复制、创建、编辑环境时所选择项目的id
        selectRows: [],
        isTesterPermission: false,
        domainVisible: false,
        conditions: [],
      }
    },
    computed: {
      searchText() {    //搜索框的文本
        return this.condition.name;
      },
      /*
      搜索后对应的总条目。搜索内容为空的话就是全部记录条数；搜索内容不为空的话就是匹配的记录条数
       */
      total() {
        return this.environments
          .filter(env => !this.searchText || env.name.toLowerCase().includes(this.searchText.toLowerCase())).length;
      },
    },
    watch: {
      //当创建及复制环境所选择的项目变化时，改变当前环境对应的projectId
      currentProjectId() {
        // el-select什么都不选时值为''，为''的话也会被当成有效的projectId传给后端，转化使其无效
        if (this.currentProjectId === '') {
          this.currentEnvironment.projectId = null;
        } else {
          this.currentEnvironment.projectId = this.currentProjectId;
        }
      }

    },

    methods: {
      showInfo(row) {
        const config = JSON.parse(row.config);
        this.conditions = config.httpConfig.conditions;
        this.domainVisible = true;
      },
      getName(row) {
        switch (row.type) {
          case "NONE":
            return this.$t("api_test.definition.document.data_set.none");
          case "MODULE":
            return this.$t("test_track.module.module");
          case "PATH":
            return this.$t("api_test.definition.api_path");
        }
      },
      getUrl(row) {
        return row.protocol + "://" + row.socket;
      },
      getDetails(row) {
        if (row && row.type === "MODULE") {
          if (row.details && row.details instanceof Array) {
            let value = "";
            row.details.forEach((item) => {
              value += item.name + ",";
            });
            if (value.endsWith(",")) {
              value = value.substr(0, value.length - 1);
            }
            return value;
          }
        } else if (row && row.type === "PATH" && row.details.length > 0 && row.details[0].name) {
          return row.details[0].value === "equals" ? this.$t("commons.adv_search.operators.equals") + row.details[0].name : this.$t("api_test.request.assertions.contains") + row.details[0].name;
        } else {
          return "";
        }
      },
      list() {
        this.environments = [];
        let url = "/project/listAll";
        this.result = this.$get(url, (response) => {   //请求未成功怎么办？
          this.projectList = response.data;  //获取当前工作空间所拥有的项目,
          this.projectList.forEach(project => {
            this.idNameMap.set(project.id, project.name);
          });
          //获取每个项目所对应的环境列表
          this.projectList.map((project) => {
            this.$get('/api/environment/list/' + project.id, response => {
              let envData = response.data;
              envData.forEach(env => {
                this.environments.push(env);
              })
            })
          })
        })
      },
      createEnv() {
        this.currentProjectId = '';
        this.dialogTitle = this.$t('api_test.environment.create');
        this.dialogVisible = true;
        this.currentEnvironment = new Environment();
      },
      search(searchText) {
      },
      editEnv(environment) {
        this.dialogTitle = this.$t('api_test.environment.config_environment');
        this.currentProjectId = environment.projectId;
        const temEnv = {};
        Object.assign(temEnv, environment);
        parseEnvironment(temEnv);   //parseEnvironment会改变环境对象的内部结构，从而影响前端列表的显示，所以复制一个环境对象作为代替
        this.currentEnvironment = temEnv;
        this.dialogVisible = true;
      },

      copyEnv(environment) {
        this.currentProjectId = environment.projectId;  //复制时默认选择所要复制环境对应的项目
        this.dialogTitle = this.$t('api_test.environment.copy_environment');
        const temEnv = {};
        Object.assign(temEnv, environment);
        parseEnvironment(temEnv);   //parseEnvironment会改变环境对象的内部结构，从而影响前端列表的显示，所以复制一个环境对象作为代替
        let newEnvironment = new Environment(temEnv);
        newEnvironment.id = null;
        newEnvironment.name = this.getNoRepeatName(newEnvironment.name);
        this.dialogVisible = true;
        this.currentEnvironment = newEnvironment;

      },
      deleteEnv(environment) {
        if (environment.id) {
          this.result = this.$get('/api/environment/delete/' + environment.id, () => {
            this.$success(this.$t('commons.delete_success'));
            this.list();
          });
        }
      },
      getNoRepeatName(name) {
        for (let i in this.environments) {
          if (this.environments[i].name === name) {
            return this.getNoRepeatName(name + ' copy');
          }
        }
        return name;
      },

      //对话框取消按钮
      close() {
        this.dialogVisible = false;
        this.$refs.environmentEdit.clearValidate();
      },
      refresh() {
        this.list();
      },
      handleSelectionChange(value) {
        this.selectRows = value;
      },
      importJSON() {
        this.$refs.envImport.open();
      },
      exportJSON() {
        if (this.selectRows.length < 1) {
          this.$warning('请选择想要导出的环境');
          return;
        }
        //拷贝一份选中的数据，不然下面删除id和projectId的时候会影响原数据
        const envs = JSON.parse(JSON.stringify(this.selectRows));

        envs.map(env => {  //不导出id和projectId
          delete env.id;
          delete env.projectId;
        })
        downloadFile('MS_' + envs.length + '_Environments.json', JSON.stringify(envs));

      },
      parseDomainName(environment) {   //解析出环境域名用于前端展示
        if (environment.config) {
          const config = JSON.parse(environment.config);
          if (config.httpConfig && !config.httpConfig.conditions) {
            if (config.httpConfig.protocol && config.httpConfig.domain) {
              return config.httpConfig.protocol + "://" + config.httpConfig.domain;
            }
            return "";
          } else {
            if (config.httpConfig.conditions.length === 1) {
              let obj = config.httpConfig.conditions[0];
              if (obj.protocol && obj.domain) {
                return obj.protocol + "://" + obj.domain;
              }
            } else if (config.httpConfig.conditions.length > 1) {
              return "SHOW_INFO";
            }
            else {
              return "";
            }
          }
        } else {  //旧版本没有对应的config数据,其域名保存在protocol和domain中
          return environment.protocol + '://' + environment.domain;
        }
      }

    },
    created() {
      this.isTesterPermission = checkoutTestManagerOrTestUser();
    },

    activated() {
      this.list();
    },


  }
</script>

<style scoped>
  .item-bar {
    width: 100%;
    background: #F9F9F9;
    padding: 5px 10px;
    box-sizing: border-box;
    border: solid 1px #e6e6e6;
  }

  .item-selected {
    background: #ECF5FF;
    border-left: solid #409EFF 5px;
  }

  .item-selected .item-right {
    visibility: visible;
  }

  .environment-edit {
    margin-left: 0;
    width: 100%;
    border: 0;
  }

  .project-item {
    padding-left: 20px;
    padding-right: 20px;
  }

  .project-item .el-select {
    margin-top: 15px;
  }
</style>