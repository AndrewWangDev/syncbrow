# Security Policy

The SyncBrow team takes the security and privacy of our users seriously. We appreciate the responsible disclosure of security vulnerabilities by security researchers and the community.

## Supported Versions

**We only accept and triage vulnerability reports for version 2.0.0 and above.**

Earlier releases (v1.x) contain deprecated legacy architectures and are end-of-life (EOL).

| Version | Supported / Accepted |
| ------- | -------------------- |
| >= 2.0.0 | :white_check_mark: Yes |
| < 2.0.0  | :x: No (EOL)          |

## Reporting a Vulnerability

**Please do NOT report security vulnerabilities through public GitHub Issues.**

If you discover a security or privacy vulnerability in SyncBrow (>= 2.0.0):

1. **GitHub Private Vulnerability Reporting (Preferred)**:
   Navigate to the repository's [Security Advisories](https://github.com/AndrewWangDev/SyncBrow/security/advisories) tab and click **"Report a vulnerability"**.
   
2. **Direct Contact**:
   If unable to use GitHub Security Advisories, send an encrypted or direct email to:
   - **Contact**: `security@andrewwangdev.com`
   - **Subject**: `[SECURITY VULNERABILITY] SyncBrow - <Brief Summary>`

### What to Include in Your Report
To help us triage and resolve the issue quickly, please include:
- SyncBrow exact version (must be >= 2.0.0).
- Type of vulnerability (e.g., cryptographic defect, DoH leak, local storage exposure, denial of service).
- Detailed step-by-step reproduction instructions or a Proof of Concept (PoC).
- Affected Android OS version and device model.
- Any suggested remediation or fix.

### Response Time & SLA
- **Initial Acknowledgement**: Within **48 hours** of receipt.
- **Triage & Assessment**: Within **5 business days**.
- **Fix & Coordinated Disclosure**: Critical vulnerabilities will be prioritized for a hotfix release within **14 business days**. We will coordinate public disclosure with you and credit your research in the official release notes.
