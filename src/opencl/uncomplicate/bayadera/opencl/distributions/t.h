#ifndef M_LOG_SQRT_PI_F
#define M_LOG_SQRT_PI_F 0.5723649429247f
#endif

inline REAL t_log_unscaled(const REAL nu, const REAL mu, const REAL sigma, const REAL x) {
    return - (0.5f * (nu + 1.0f) * native_log(1.0f + pown((x - mu) / sigma, 2) / nu));
}

inline REAL t_log_scale(const REAL nu, const REAL sigma) {
    return lgamma(0.5f * (nu + 1.0f)) - lgamma(0.5f * nu)
        - M_LOG_SQRT_PI_F- 0.5f * native_log(nu) - native_log(sigma);
}

inline REAL t_log(const REAL nu, const REAL mu, const REAL sigma, const REAL x) {
    return t_log_unscaled(nu, mu, sigma, x) + t_log_scale(nu, sigma);
}

// ============= With params ========================================

REAL t_mcmc_logpdf(__constant const REAL* params, const REAL* x) {
    return t_log_unscaled(params[0], params[1], params[2], x[0]);
}

REAL t_logpdf(__constant const REAL* params, const REAL* x) {
    return t_log_unscaled(params[0], params[1], params[2], x[0]) + params[3];
}

REAL t_loglik(__constant const REAL* data, const REAL* nu_mu_sigma) {
    const uint n = (uint) data[0];
    const REAL nu = nu_mu_sigma[0];
    const REAL mu = nu_mu_sigma[1];
    const REAL sigma = nu_mu_sigma[2];
    const bool valid = (0.0f < nu) && (0.0f < sigma);
    if (valid) {
        const REAL scale =  t_log_scale(nu, sigma);
        REAL res = 0.0;
        for (uint i = 0; i < n; i++){
            res += t_log_unscaled(nu, mu, sigma, data[i+1]) + scale;
        }
        return res;
    }
    return NAN;

}
