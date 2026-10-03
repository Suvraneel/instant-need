# Block public invoice downloads

Invoices are stored as `invoices/<opaque-id>.pdf` in the same S3 bucket as
public catalog images. The backend retains the S3 object location so the admin
API can read the PDF with its IAM identity. A CloudFront URL must not be a
customer download route.

1. In the CloudFront distribution used by production `STORAGE_BASE_URL`, create
   a CloudFront Function from `block-invoices.js`, test it, and publish it.
2. Associate it with **viewer request** on every cache behavior that can route
   to this bucket. If another viewer-request function is already associated,
   incorporate this check into that function. Keep public product/category
   image behavior intact.
3. Confirm the S3 bucket and objects do not allow anonymous direct S3 access.
   CloudFront blocking alone is insufficient when the S3 origin is public.
   Preserve the backend IAM principal's `s3:GetObject` access to `invoices/*`.
4. Wait for the distribution to reach **Deployed**, then invalidate
   `/invoices/*` to remove previously cached PDFs. This does not retract files
   already downloaded or cached on a customer's device.
5. Request a known, previously issued invoice CloudFront URL without a token:
   it must return 403. Verify an existing product image still returns 200 and
   the admin invoice API still returns a PDF for an admin account. Repeat with
   direct S3 URL access to confirm it is denied anonymously.

The repository cannot apply this configuration by itself: CloudFront behavior
associations, S3 public access, and invalidations belong to the AWS account.
See [AWS's function association guide](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/associate-function.html)
and [invalidation guide](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html).
