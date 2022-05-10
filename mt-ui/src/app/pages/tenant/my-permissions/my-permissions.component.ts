import { ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { PageEvent } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { FormInfoService } from 'mt-form-builder';
import { IForm, IOption } from 'mt-form-builder/lib/classes/template.interface';
import { combineLatest, Observable, of } from 'rxjs';
import { map, switchMap, take, tap } from 'rxjs/operators';
import { ISumRep, SummaryEntityComponent } from 'src/app/clazz/summary.component';
import { TenantSummaryEntityComponent } from 'src/app/clazz/tenant-summary.component';
import { IPermission } from 'src/app/clazz/validation/aggregate/permission/interface-permission';
import { IProjectSimple } from 'src/app/clazz/validation/aggregate/project/interface-project';
import { INode } from 'src/app/components/dynamic-tree/dynamic-tree.component';
import { ISearchConfig } from 'src/app/components/search/search.component';
import { ISearchEvent } from 'src/app/components/tenant-search/tenant-search.component';
import { FORM_CONFIG } from 'src/app/form-configs/view-less.config';
import { DeviceService } from 'src/app/services/device.service';
import { EndpointService } from 'src/app/services/endpoint.service';
import { HttpProxyService } from 'src/app/services/http-proxy.service';
import { MyPermissionService } from 'src/app/services/my-permission.service';
import { ProjectService } from 'src/app/services/project.service';
import { PermissionComponent } from '../permission/permission.component';

@Component({
  selector: 'app-my-permissions',
  templateUrl: './my-permissions.component.html',
  styleUrls: ['./my-permissions.component.css']
})
export class MyPermissionsComponent extends TenantSummaryEntityComponent<IPermission, IPermission> implements OnDestroy {
  public formId = "permissionTableColumnConfig";
  formId2 = 'summaryPermissionCustomerView';
  formInfo: IForm = JSON.parse(JSON.stringify(FORM_CONFIG));
  viewType: "LIST_VIEW" | "DYNAMIC_TREE_VIEW" = "LIST_VIEW";
  public apiRootId: string;
  private formCreatedOb2: Observable<string>;
  columnList: any = {};
  sheetComponent = PermissionComponent;
  apiDataSource: MatTableDataSource<IPermission>;
  apiTotoalItemCount = 0;
  apiPageNumber: number = 0;
  apiPageSize: number = 11;
  public loadRoot;
  public loadChildren = (id: string) => {
    if (id === this.apiRootId) {
      return this.entitySvc.readEntityByQuery(0, 1000, "parentId:" + id).pipe(switchMap(data => {
        const epIds = data.data.map(e => e.name)
        return this.epSvc.readEntityByQuery(0, epIds.length, 'ids:' + epIds.join('.')).pipe(switchMap(resp => {
          data.data.forEach(e => e.name = resp.data.find(ee => ee.id === e.name).description)
          return of(data)
        }))
      }))
    } else {
      return this.entitySvc.readEntityByQuery(0, 1000, "parentId:" + id)
    }
  }
  searchConfigs: ISearchConfig[] = [
    {
      searchLabel: 'ID',
      searchValue: 'id',
      type: 'text',
      multiple: {
        delimiter: '.'
      }
    },
  ]
  constructor(
    public entitySvc: MyPermissionService,
    public epSvc: EndpointService,
    public projectSvc: ProjectService,
    public deviceSvc: DeviceService,
    public httpSvc: HttpProxyService,
    public fis: FormInfoService,
    public bottomSheet: MatBottomSheet,
    public route: ActivatedRoute,
    private translate: TranslateService,
  ) {
    super(route, projectSvc, httpSvc, entitySvc, deviceSvc, bottomSheet, fis, 5);
    const sub = this.projectId.subscribe(next => {
      this.entitySvc.setProjectId(next);
      this.loadRoot = this.entitySvc.readEntityByQuery(0, 1000, "types:COMMON.PROJECT,parentId:null")
      this.loadChildren = (id: string) => {
        return this.entitySvc.readEntityByQuery(0, 1000, "parentId:" + id) .pipe(map(e => {
          e.data.forEach(ee => {
            if (next === '0P8HE307W6IO') {
              (ee as INode).enableI18n = true;
            }
          })
          return e
        }));
      }
    });
    const sub2 = this.canDo('VIEW_PERMISSION').subscribe(b => {
      if (b.result) {
        this.doSearch({ value: 'types:COMMON', resetPage: true })
        this.entitySvc.readEntityByQuery(this.apiPageNumber, this.apiPageSize, 'types:API').subscribe(next => {
          this.updateApiSummaryData(next)
        })
      }
    })
    const sub3 = this.canDo('EDIT_PERMISSION').subscribe(b => {
      this.columnList = b.result ? {
        id: 'ID',
        name: 'NAME',
        description: 'DESCRIPTION',
        type: 'TYPE',
        edit: 'EDIT',
        clone: 'CLONE',
        delete: 'DELETE',
      } : {
        id: 'ID',
        name: 'NAME',
        description: 'DESCRIPTION',
        type: 'TYPE',
      }
    })
    this.subs.add(sub)
    this.subs.add(sub2)
    this.subs.add(sub3)
    this.formCreatedOb2 = this.fis.formCreated(this.formId2);

    combineLatest([this.formCreatedOb2]).pipe(take(1)).subscribe(() => {
      const sub = this.fis.formGroupCollection[this.formId2].valueChanges.subscribe(e => {
        this.viewType = e.view;
      });
      if (!this.fis.formGroupCollection[this.formId2].get('view').value) {
        this.fis.formGroupCollection[this.formId2].get('view').setValue(this.viewType);
      }
      this.subs.add(sub)
    })
  }
  ngOnDestroy() {
    this.fis.reset(this.formId)
    this.fis.reset(this.formId2)
  };
  getOption(value: string, options: IOption[]) {
    return options.find(e => e.value == value)
  }
  doSearchWrapperCommon(config: ISearchEvent) {
    config.value = "types:COMMON"
    this.doSearch(config)
  }
  private updateApiSummaryData(next: ISumRep<IPermission>) {
    if (next.data) {
      this.apiDataSource = new MatTableDataSource(next.data);
      this.apiTotoalItemCount = next.totalItemCount;
    } else {
      this.apiDataSource = new MatTableDataSource([]);
      this.apiTotoalItemCount = 0;
    }
  }
  displayedApiColumns() {
    return['id','name']
  };
  apiPageHandler(e: PageEvent) {
    this.apiPageNumber = e.pageIndex;
    this.entitySvc.readEntityByQuery(this.apiPageNumber, this.apiPageSize, 'types:API').subscribe(next => {
      this.updateApiSummaryData(next);
    });
  }
}