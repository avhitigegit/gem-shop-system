/** The four roles (BRD A2). Same names as the backend (lk.nuvi.gemshop.auth.user.Role). */
export type Role = "OWNER" | "SALESPERSON" | "STAFF" | "ACCOUNTANT";

export const OWNER: Role = "OWNER";
export const SALESPERSON: Role = "SALESPERSON";
export const STAFF: Role = "STAFF";
export const ACCOUNTANT: Role = "ACCOUNTANT";
export const ALL_ROLES: Role[] = [OWNER, SALESPERSON, STAFF, ACCOUNTANT];

export const ROLE_LABEL: Record<Role, string> = {
  OWNER: "Owner",
  SALESPERSON: "Salesperson",
  STAFF: "Shop Staff",
  ACCOUNTANT: "Accountant",
};

/** Owner and Accountant see all branches; the others only their own branch (BRD rule 9). */
export const seesAllBranches = (role: Role) => role === OWNER || role === ACCOUNTANT;
