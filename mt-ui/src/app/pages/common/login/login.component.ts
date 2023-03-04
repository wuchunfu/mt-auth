import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { IOption } from 'mt-form-builder/lib/classes/template.interface';
import { IForgetPasswordRequest, IPendingResourceOwner } from 'src/app/clazz/validation/aggregate/user/interfaze-user';
import { UserValidator } from 'src/app/clazz/validation/aggregate/user/validator-user';
import { IMfaResponse, ITokenResponse } from 'src/app/clazz/validation/interfaze-common';
import { ErrorMessage, StringValidator } from 'src/app/clazz/validation/validator-common';
import { MsgBoxComponent } from 'src/app/components/msg-box/msg-box.component';
import { AuthService } from 'src/app/services/auth.service';
import { HttpProxyService } from 'src/app/services/http-proxy.service';
import * as UUID from 'uuid/v1';
@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  nextUrl: string = '/home';
  forgetPwd: boolean = false;
  isRegister: boolean = true;
  emailErrorMsg: string = undefined;
  activationCodeErrorMsg: string = undefined;
  tokenErrorMsg: string = undefined;
  mobileNumberError: string = undefined;
  countryCodeError: string = undefined;
  passwordErrorMsg: string = undefined;
  confirmgPasswordErrorMsg: string = undefined;
  activationCodeChangeId = UUID();
  registerChangeId = UUID();
  tokenChangeId = UUID();
  resetChangeId = UUID();
  loginOrRegForm = new FormGroup({
    isRegister: new FormControl('', []),
    email: new FormControl('', []),
    pwd: new FormControl('', []),
    confirmPwd: new FormControl('', []),
    activationCode: new FormControl('', []),
    token: new FormControl('', []),
    countryCode: new FormControl('', []),
    mobileNumber: new FormControl('', []),
  });
  private validator = new UserValidator()
  constructor(
    public httpProxy: HttpProxyService,
    private route: Router,
    public dialog: MatDialog,
    private router: ActivatedRoute,
    public translate: TranslateService,
    public authSvc: AuthService
  ) {
    this.httpProxy.refreshInprogress = false;
    this.router.queryParamMap.subscribe(queryMaps => {
      if (queryMaps.get('redirect_uri') !== null) {
        /** get  authorize party info */
        this.nextUrl = '/authorize';
      }
    })
  }
  mobileNums: IOption[] = [
    {
      label: '+1', value: '1'
    },
    {
      label: '+86', value: '86'
    },
  ]
  ngOnInit() {
  }
  login() {
    let error: ErrorMessage[] = [];
    StringValidator.isEmail(this.loginOrRegForm.get('email').value, error, 'email')
    if (error.length > 0) {
      if (error.some(e => e.key === 'email')) {
        this.emailErrorMsg = error.find(e => e.key === 'email').message;
        this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
      }
      if (error.some(e => e.key === 'pwd')) {
        this.passwordErrorMsg = error.find(e => e.key === 'pwd').message;
        this.loginOrRegForm.get('pwd').setErrors({ wrongValue: true });
      }

      this.loginOrRegForm.valueChanges.subscribe(() => {
        let error: ErrorMessage[] = [];
        StringValidator.isEmail(this.loginOrRegForm.get('email').value, error, 'email')
        StringValidator.hasValidWhiteListValue(this.loginOrRegForm.get('pwd').value, error, 'pwd')
        if (error.some(e => e.key === 'email')) {
          this.emailErrorMsg = error.find(e => e.key === 'email').message;
          this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
        } else {
          this.emailErrorMsg = undefined;
        }
        if (error.some(e => e.key === 'pwd')) {
          this.passwordErrorMsg = error.find(e => e.key === 'pwd').message;
          this.loginOrRegForm.get('pwd').setErrors({ wrongValue: true });
        } else {
          this.passwordErrorMsg = undefined;
        }
      })
    } else {
      this.httpProxy.login(this.loginOrRegForm).subscribe(next => {
        if ((next as IMfaResponse).mfaId) {
          this.authSvc.loginFormValue = this.loginOrRegForm;
          this.authSvc.loginNextUrl = this.nextUrl;
          this.authSvc.mfaId = (next as IMfaResponse).mfaId;
          this.route.navigate(['/mfa'], { queryParams: this.router.snapshot.queryParams });
        } else {
          this.httpProxy.currentUserAuthInfo = next as ITokenResponse;
          this.route.navigate([this.nextUrl], { queryParams: this.router.snapshot.queryParams });
        }
      })
    }
  }
  register() {
    let error = this.validator.validate(this.getRegPayload(this.loginOrRegForm), 'appCreateUserCommandValidator');
    if (this.loginOrRegForm.get('confirmPwd').value !== this.loginOrRegForm.get('pwd').value) {
      this.confirmgPasswordErrorMsg = 'PWD_NOT_SAME';
      this.loginOrRegForm.get('confirmPwd').setErrors({ wrongValue: true });
    }
    if (error.length > 0) {
      if (error.some(e => e.key === 'email')) {
        this.emailErrorMsg = error.find(e => e.key === 'email').message;
        this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
      }
      if (error.some(e => e.key === 'countryCode')) {
        this.countryCodeError = 'INVALID_COUNTRY_CODE';
        this.loginOrRegForm.get('countryCode').setErrors({ wrongValue: true });
      }
      if (error.some(e => e.key === 'mobileNumber')) {
        this.mobileNumberError = 'INVALID_MOBILE';
        this.loginOrRegForm.get('mobileNumber').setErrors({ wrongValue: true });
      }
      if (error.some(e => e.key === 'activationCode')) {
        this.activationCodeErrorMsg = error.find(e => e.key === 'activationCode').message;
        this.loginOrRegForm.get('activationCode').setErrors({ wrongValue: true });
      }
      if (error.some(e => e.key === 'password')) {
        this.passwordErrorMsg = error.find(e => e.key === 'password').message;
        this.loginOrRegForm.get('pwd').setErrors({ wrongValue: true });
      }

      this.loginOrRegForm.valueChanges.subscribe(() => {
        let error = this.validator.validate(this.getRegPayload(this.loginOrRegForm), 'appCreateUserCommandValidator');
        if (error.some(e => e.key === 'email')) {
          this.emailErrorMsg = error.find(e => e.key === 'email').message;
          this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
        } else {
          this.emailErrorMsg = undefined;
        }
        if (error.some(e => e.key === 'activationCode')) {
          this.activationCodeErrorMsg = error.find(e => e.key === 'activationCode').message;
          this.loginOrRegForm.get('activationCode').setErrors({ wrongValue: true });
        } else {
          this.activationCodeErrorMsg = undefined;
        }
        if (error.some(e => e.key === 'password')) {
          this.passwordErrorMsg = error.find(e => e.key === 'password').message;
          this.loginOrRegForm.get('pwd').setErrors({ wrongValue: true });
        } else {
          this.passwordErrorMsg = undefined;
        }
        if (this.loginOrRegForm.get('confirmPwd').value !== this.loginOrRegForm.get('pwd').value) {
          this.confirmgPasswordErrorMsg = 'PWD_NOT_SAME';
          this.loginOrRegForm.get('confirmPwd').setErrors({ wrongValue: true });
        } else {
          this.confirmgPasswordErrorMsg = undefined;
        }
      })
    } else {
      if (this.loginOrRegForm.get('confirmPwd').value === this.loginOrRegForm.get('pwd').value) {
        this.httpProxy.register(this.getRegPayload(this.loginOrRegForm), this.registerChangeId).subscribe(next => {
          this.loginOrRegForm.get('isRegister').setValue(false);
          this.openDialog('REGISTER_SUCCESS_MSG');
        })
      }
    }

  }
  getCode() {
    let error = this.validator.validate(this.getActivatePayload(this.loginOrRegForm), 'appCreatePendingUserCommandValidator');
    if (error.length > 0) {
      this.emailErrorMsg = error[0].message;
      this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
      this.loginOrRegForm.get('email').valueChanges.subscribe(() => {
        let error = this.validator.validate(this.getActivatePayload(this.loginOrRegForm), 'appCreatePendingUserCommandValidator');
        if (error.length > 0) {
          this.emailErrorMsg = error[0].message;
          this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
        } else {
          this.emailErrorMsg = undefined;
        }
      })
    } else {
      this.httpProxy.currentUserAuthInfo = undefined;
      this.httpProxy.activate(this.getActivatePayload(this.loginOrRegForm), this.activationCodeChangeId).subscribe(next => {
        this.openDialog('CODE_SEND_MSG');
      })
    }
  }
  getToken() {
    let error = this.validator.validate(this.getForgetPayload(this.loginOrRegForm), 'appForgetUserPasswordCommandValidator');
    if (error.length > 0) {
      this.emailErrorMsg = error[0].message;
      this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
      this.loginOrRegForm.get('email').valueChanges.subscribe(() => {
        let error = this.validator.validate(this.getForgetPayload(this.loginOrRegForm), 'appForgetUserPasswordCommandValidator');
        if (error.length > 0) {
          this.emailErrorMsg = error[0].message;
          this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
        } else {
          this.emailErrorMsg = undefined;
        }
      })
    } else {
      this.httpProxy.currentUserAuthInfo = undefined;
      this.httpProxy.forgetPwd(this.getForgetPayload(this.loginOrRegForm), this.tokenChangeId).subscribe(next => {
        this.openDialog('TOKEN_SEND_MSG');
      })
    }
  }
  changePassword() {
    let error = this.validator.validate(this.getResetPayload(this.loginOrRegForm), 'appResetUserPasswordCommandValidator');
    if (this.loginOrRegForm.get('confirmPwd').value !== this.loginOrRegForm.get('pwd').value) {
      this.confirmgPasswordErrorMsg = 'PWD_NOT_SAME';
      this.loginOrRegForm.get('confirmPwd').setErrors({ wrongValue: true });
    }
    if (error.length > 0) {
      if (error.some(e => e.key === 'email')) {
        this.emailErrorMsg = error.find(e => e.key === 'email').message;
        this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
      }
      if (error.some(e => e.key === 'token')) {
        this.tokenErrorMsg = error.find(e => e.key === 'token').message;
        this.loginOrRegForm.get('token').setErrors({ wrongValue: true });
      }
      if (error.some(e => e.key === 'newPassword')) {
        this.passwordErrorMsg = error.find(e => e.key === 'newPassword').message;
        this.loginOrRegForm.get('pwd').setErrors({ wrongValue: true });
      }

      this.loginOrRegForm.valueChanges.subscribe(() => {
        let error = this.validator.validate(this.getResetPayload(this.loginOrRegForm), 'appResetUserPasswordCommandValidator');
        if (error.some(e => e.key === 'email')) {
          this.emailErrorMsg = error.find(e => e.key === 'email').message;
          this.loginOrRegForm.get('email').setErrors({ wrongValue: true });
        } else {
          this.emailErrorMsg = undefined;
        }
        if (error.some(e => e.key === 'token')) {
          this.tokenErrorMsg = error.find(e => e.key === 'token').message;
          this.loginOrRegForm.get('token').setErrors({ wrongValue: true });
        } else {
          this.tokenErrorMsg = undefined;
        }
        if (error.some(e => e.key === 'newPassword')) {
          this.passwordErrorMsg = error.find(e => e.key === 'newPassword').message;
          this.loginOrRegForm.get('pwd').setErrors({ wrongValue: true });
        } else {
          this.passwordErrorMsg = undefined;
        }
        if (this.loginOrRegForm.get('confirmPwd').value !== this.loginOrRegForm.get('pwd').value) {
          this.confirmgPasswordErrorMsg = 'PWD_NOT_SAME';
          this.loginOrRegForm.get('confirmPwd').setErrors({ wrongValue: true });
        } else {
          this.confirmgPasswordErrorMsg = undefined;
        }
      })
    } else {
      if (this.loginOrRegForm.get('confirmPwd').value === this.loginOrRegForm.get('pwd').value) {
        this.httpProxy.resetPwd(this.getResetPayload(this.loginOrRegForm), this.resetChangeId).subscribe(next => {
          this.loginOrRegForm.get('isRegister').setValue(false);
          this.forgetPwd = false;
          this.openDialog('PASSWORD_UPDATE_SUCCESS_MSG');
        })
      }
    }
  }
  openDialog(msg: string): void {
    this.dialog.open(MsgBoxComponent, {
      width: '250px',
      data: msg
    });
  }
  private getActivatePayload(fg: FormGroup): IPendingResourceOwner {
    return {
      email: fg.get('email').value,
    };
  }
  private getRegPayload(fg: FormGroup): IPendingResourceOwner {
    return {
      email: fg.get('email').value,
      password: fg.get('pwd').value,
      activationCode: fg.get('activationCode').value,
      mobileNumber: fg.get('mobileNumber').value,
      countryCode: fg.get('countryCode').value,
    };
  }
  private getResetPayload(fg: FormGroup): IForgetPasswordRequest {
    return {
      email: fg.get('email').value,
      token: fg.get('token').value,
      newPassword: fg.get('pwd').value,
    };
  }
  private getForgetPayload(fg: FormGroup): IForgetPasswordRequest {
    return {
      email: fg.get('email').value,
    };
  }
  public toggleLang() {
    if (this.translate.currentLang === 'enUS') {
      this.translate.use('zhHans')
      this.translate.get('DOCUMENT_TITLE').subscribe(
        next => {
          document.title = next
          document.documentElement.lang = 'zh-Hans'
        }
      )
    }
    else {
      this.translate.use('enUS')
      this.translate.get('DOCUMENT_TITLE').subscribe(
        next => {
          document.title = next
          document.documentElement.lang = 'en'
        }
      )
    }
  }
  openDoc(){
    window.open('./docs', '_blank').focus();
  }
  openGithub(){
    window.open('https://github.com/publicdevop2019/mt-auth', '_blank').focus();
  }
}
