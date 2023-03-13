import { Component, OnInit } from '@angular/core';
import { HttpProxyService } from 'src/app/services/http-proxy.service';
export interface IMgmtDashboardInfo {
  totalProjects: number;
  totalClients: number;
  totalEndpoint: number;
  totalSharedEndpoint: number;
  totalPublicEndpoint: number;
  totalUser: number;
}
@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  summary: IMgmtDashboardInfo;
  constructor(public httpProxy: HttpProxyService) { }

  ngOnInit(): void {
    this.httpProxy.getMgmtDashboardInfo().subscribe((data) => {
      this.summary = data;
    })
  }

}
