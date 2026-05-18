import { Component, HostListener, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { Cart } from '../cart';
import { ShippingAddress } from '../user';
import { CartService } from '../cart.service';
import { UserService } from '../user.service';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-shipping-modal',
  templateUrl: './shipping-modal.component.html',
  styleUrls: ['./shipping-modal.component.css'],
  standalone: false
})
export class ShippingModalComponent implements OnInit {
  @Input() cart!: Cart;
  /** Emitted after a successful save (Pay Later path). */
  @Output() saved = new EventEmitter<Cart>();
  /** Emitted after a successful save when the user chose Pay Now. */
  @Output() payNowRequested = new EventEmitter<Cart>();
  @Output() closed = new EventEmitter<void>();

  street1 = '';
  street2 = '';
  city = '';
  state = '';
  zip = '';
  phone = '';
  saveToProfile = false;
  hasExistingProfileAddress = false;

  saving = false;
  error = '';

  constructor(
    private cartService: CartService,
    private userService: UserService,
    public auth: AuthService
  ) {}

  ngOnInit() {
    const user = this.auth.currentUser$.value;
    if (user?.shippingAddress) {
      this.hasExistingProfileAddress = true;
      this.street1 = user.shippingAddress.street1 ?? '';
      this.street2 = user.shippingAddress.street2 ?? '';
      this.city = user.shippingAddress.city ?? '';
      this.state = user.shippingAddress.state ?? '';
      this.zip = user.shippingAddress.zip ?? '';
      this.phone = user.shippingAddress.phone ?? user.phone ?? '';
    } else if (user?.phone) {
      this.phone = user.phone;
    }
  }

  isValid(): boolean {
    return !!this.street1.trim() && !!this.city.trim() && !!this.state.trim() && !!this.zip.trim();
  }

  private buildAddress(): ShippingAddress {
    return {
      street1: this.street1.trim(),
      street2: this.street2.trim() || undefined,
      city: this.city.trim(),
      state: this.state.trim(),
      zip: this.zip.trim(),
      phone: this.phone.trim() || undefined
    };
  }

  private doSave(onSuccess: (cart: Cart) => void) {
    if (!this.isValid()) return;
    this.saving = true;
    this.error = '';
    const address = this.buildAddress();
    this.cartService.saveShippingAddress(address).subscribe({
      next: updatedCart => {
        if (this.saveToProfile) {
          this.userService.updateProfileAddress(address).subscribe({
            next: updatedUser => this.auth.currentUser$.next(updatedUser),
            error: () => {}
          });
        }
        this.saving = false;
        onSuccess(updatedCart);
      },
      error: () => {
        this.saving = false;
        this.error = 'Failed to save shipping address. Please try again.';
      }
    });
  }

  saveAndPayNow() {
    this.doSave(cart => {
      this.payNowRequested.emit(cart);
    });
  }

  saveAndPayLater() {
    this.doSave(cart => {
      this.saved.emit(cart);
      this.closed.emit();
    });
  }

  close() {
    this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (!this.saving) this.close();
  }
}
