!(function () {
  if (
    window.location.href.indexOf("mobile") !== -1 &&
    window.location.href.indexOf("password") !== -1
  ) {
    var hostname = window.location.hostname;
    var domain = hostname.slice(hostname.indexOf(".") + 1);
    var params = window.location.href.slice(
      window.location.href.indexOf("?") + 1
    );
    window.location.replace(
      "http://cy-plugin." +
        domain +
        "/#/windowOrders-li/orderList/takeoutList?isGw=1&" +
        params
    );
  }
})();

!(function () {
  if (window.globalThis === undefined) {
    window.globalThis = window;
  }
})();
